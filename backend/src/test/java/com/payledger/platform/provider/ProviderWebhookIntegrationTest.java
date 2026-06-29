package com.payledger.platform.provider;

import tools.jackson.databind.ObjectMapper;
import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.identity.application.CustomerIdentityService;
import com.payledger.platform.kyc.application.KycOperationsService;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.merchant.application.MerchantDetails;
import com.payledger.platform.merchant.application.MerchantService;
import com.payledger.platform.merchant.application.OnboardMerchantCommand;
import com.payledger.platform.payment.api.CreatePaymentIntentRequest;
import com.payledger.platform.payment.api.PaymentIntentResponse;
import com.payledger.platform.payment.domain.PaymentIntentStatus;
import com.payledger.platform.provider.application.ProviderSimulationResult;
import com.payledger.platform.provider.application.ProviderSimulatorService;
import com.payledger.platform.provider.application.ProviderWebhookRequest;
import com.payledger.platform.provider.application.ProviderWebhookService;
import com.payledger.platform.provider.domain.ProviderTransactionStatus;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.FundsHoldStatus;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ProviderWebhookIntegrationTest extends PostgresIntegrationTest {

    private static final String OPERATIONS_ACTOR =
            "provider-webhook-test-operations-actor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerIdentityService customerIdentityService;

    @Autowired
    private KycOperationsService kycOperationsService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private ProviderSimulatorService providerSimulatorService;

    @Autowired
    private ProviderWebhookService webhookService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void validSignedWebhookIsAcceptedOnce() throws Exception {
        PaymentContext context = createAuthorizedPayment(
                "valid-webhook",
                2_500
        );
        ProviderSimulationResult providerTransaction =
                providerSimulatorService.createTransaction(
                        context.paymentIntentId(),
                        ProviderTransactionStatus.SUCCEEDED
                );
        String payload = payload(
                "evt-valid-" + UUID.randomUUID(),
                "PAYMENT_SUCCEEDED",
                providerTransaction.providerTransactionId(),
                context.paymentIntentId()
        );
        String signature = webhookService.signatureFor(payload);

        mockMvc.perform(
                        post("/api/v1/provider/webhooks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .header("X-PayLedger-Signature", signature)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        mockMvc.perform(
                        post("/api/v1/provider/webhooks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .header("X-PayLedger-Signature", signature)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(countWebhookEvents(context.paymentIntentId())).isOne();
        assertThat(countJournalEntries(
                "PAYMENT_CAPTURE",
                context.paymentIntentId()
        )).isOne();
        assertThat(paymentStatus(context.paymentIntentId()))
                .isEqualTo(PaymentIntentStatus.CAPTURED.name());
        assertThat(providerStatus(providerTransaction.providerTransactionId()))
                .isEqualTo(ProviderTransactionStatus.SUCCEEDED.name());
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        PaymentContext context = createAuthorizedPayment(
                "invalid-signature",
                1_000
        );
        ProviderSimulationResult providerTransaction =
                providerSimulatorService.createTransaction(
                        context.paymentIntentId(),
                        ProviderTransactionStatus.SUCCEEDED
                );
        String payload = payload(
                "evt-invalid-" + UUID.randomUUID(),
                "PAYMENT_SUCCEEDED",
                providerTransaction.providerTransactionId(),
                context.paymentIntentId()
        );

        mockMvc.perform(
                        post("/api/v1/provider/webhooks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .header("X-PayLedger-Signature", "bad-signature")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(countWebhookEvents(context.paymentIntentId())).isZero();
        assertThat(countJournalEntries(
                "PAYMENT_CAPTURE",
                context.paymentIntentId()
        )).isZero();
    }

    @Test
    void outOfOrderUnknownTransactionIsIgnored() throws Exception {
        UUID paymentIntentId = UUID.randomUUID();
        String eventId = "evt-unknown-" + UUID.randomUUID();
        String payload = payload(
                eventId,
                "PAYMENT_SUCCEEDED",
                "fpt_unknown_" + UUID.randomUUID().toString().replace("-", ""),
                paymentIntentId
        );

        mockMvc.perform(
                        post("/api/v1/provider/webhooks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .header(
                                        "X-PayLedger-Signature",
                                        webhookService.signatureFor(payload)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"))
                .andExpect(jsonPath("$.ignoredReason")
                        .value("UNKNOWN_PROVIDER_TRANSACTION"));

        assertThat(countWebhookEventsByProviderEventId(eventId)).isOne();
        assertThat(countJournalEntries(
                "PAYMENT_CAPTURE",
                paymentIntentId
        )).isZero();
    }

    @Test
    void providerFailureLeavesAuthorizedPaymentRecoverable()
            throws Exception {
        PaymentContext context = createAuthorizedPayment(
                "provider-failure",
                1_750
        );
        ProviderSimulationResult providerTransaction =
                providerSimulatorService.createTransaction(
                        context.paymentIntentId(),
                        ProviderTransactionStatus.FAILED
                );
        String payload = payload(
                "evt-failed-" + UUID.randomUUID(),
                "PAYMENT_FAILED",
                providerTransaction.providerTransactionId(),
                context.paymentIntentId()
        );

        mockMvc.perform(
                        post("/api/v1/provider/webhooks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .header(
                                        "X-PayLedger-Signature",
                                        webhookService.signatureFor(payload)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(paymentStatus(context.paymentIntentId()))
                .isEqualTo(PaymentIntentStatus.AUTHORIZED.name());
        assertThat(countFundsHolds(
                context.paymentIntentId(),
                FundsHoldStatus.ACTIVE
        )).isOne();
        assertThat(countJournalEntries(
                "PAYMENT_CAPTURE",
                context.paymentIntentId()
        )).isZero();
        assertThat(providerStatus(providerTransaction.providerTransactionId()))
                .isEqualTo(ProviderTransactionStatus.FAILED.name());
    }

    private PaymentContext createAuthorizedPayment(
            String label,
            long amountMinor
    ) throws Exception {
        WalletContext walletContext = createApprovedWallet(label, "TRY");
        String subject = linkSubject(walletContext.customer());
        MerchantDetails merchant = createActiveMerchant(label, "TRY");
        topUp(walletContext.ledgerAccount(), 10_000, "TRY");

        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest(
                walletContext.wallet().getId(),
                merchant.id(),
                amountMinor,
                "TRY"
        );

        String response = mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header(
                                        "Idempotency-Key",
                                        "provider-payment-" + UUID.randomUUID()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(subject))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        PaymentIntentResponse paymentIntent = objectMapper.readValue(
                response,
                PaymentIntentResponse.class
        );

        return new PaymentContext(paymentIntent.id());
    }

    private WalletContext createApprovedWallet(String label, String currency) {
        String suffix = uniqueSuffix();
        Customer customer = customerService.createCustomer(
                CustomerType.PERSONAL,
                "Test " + label + " " + suffix,
                label + "-" + suffix + "@example.test"
        );
        approveKyc(customer);
        Wallet wallet = walletService.createWallet(customer.getId(), currency);
        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();
        return new WalletContext(customer, wallet, ledgerAccount);
    }

    private void approveKyc(Customer customer) {
        String actor = "kyc-provider-webhook-test-actor-" + UUID.randomUUID();
        kycOperationsService.submitForReview(
                customer.getId(),
                actor,
                "Prepare customer for provider webhook testing."
        );
        kycOperationsService.approve(
                customer.getId(),
                actor,
                "Approve customer for provider webhook testing."
        );
    }

    private String linkSubject(Customer customer) {
        String subject = "provider-webhook-subject-" + UUID.randomUUID();
        customerIdentityService.linkKeycloakIdentity(customer.getId(), subject);
        return subject;
    }

    private MerchantDetails createActiveMerchant(
            String label,
            String currency
    ) {
        MerchantDetails merchant = merchantService.onboard(
                new OnboardMerchantCommand(
                        "Provider Merchant " + label + " " + uniqueSuffix(),
                        "Provider Merchant " + label + " " + uniqueSuffix(),
                        currency,
                        1,
                        OPERATIONS_ACTOR,
                        "Onboard merchant for provider webhook testing."
                )
        );
        return merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate merchant for provider webhook testing."
        );
    }

    private void topUp(
            LedgerAccount walletAccount,
            long amountMinor,
            String currency
    ) {
        LedgerAccount platformCash = createPlatformCashAccount(currency);
        ledgerService.post(
                new PostJournalEntryCommand(
                        "WALLET_TOP_UP",
                        "PROVIDER_WEBHOOK_TEST_TOP_UP",
                        UUID.randomUUID(),
                        currency,
                        "Fund a wallet before provider webhook testing.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        platformCash.getId(),
                                        PostingDirection.DEBIT,
                                        amountMinor
                                ),
                                new LedgerPostingCommand(
                                        walletAccount.getId(),
                                        PostingDirection.CREDIT,
                                        amountMinor
                                )
                        )
                )
        );
    }

    private LedgerAccount createPlatformCashAccount(String currency) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);

        return ledgerAccountRepository.saveAndFlush(
                LedgerAccount.createPlatformAccount(
                        "PROVIDER_WEBHOOK_TEST_CASH_" + suffix,
                        LedgerAccountType.ASSET,
                        currency
                )
        );
    }

    private String payload(
            String eventId,
            String eventType,
            String providerTransactionId,
            UUID paymentIntentId
    ) throws Exception {
        return objectMapper.writeValueAsString(
                new ProviderWebhookRequest(
                        eventId,
                        eventType,
                        providerTransactionId,
                        paymentIntentId
                )
        );
    }

    private String paymentStatus(UUID paymentIntentId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT status
                        FROM payment_intents
                        WHERE id = ?
                        """,
                String.class,
                paymentIntentId
        );
    }

    private String providerStatus(String providerTransactionId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT status
                        FROM provider_transactions
                        WHERE provider_transaction_id = ?
                        """,
                String.class,
                providerTransactionId
        );
    }

    private long countWebhookEvents(UUID paymentIntentId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM provider_webhook_events
                        WHERE payment_intent_id = ?
                        """,
                Long.class,
                paymentIntentId
        );
    }

    private long countWebhookEventsByProviderEventId(String providerEventId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM provider_webhook_events
                        WHERE provider_event_id = ?
                        """,
                Long.class,
                providerEventId
        );
    }

    private long countJournalEntries(
            String journalType,
            UUID paymentIntentId
    ) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM journal_entries
                        WHERE journal_type = ?
                          AND reference_type = 'PAYMENT_INTENT'
                          AND reference_id = ?
                        """,
                Long.class,
                journalType,
                paymentIntentId
        );
    }

    private long countFundsHolds(
            UUID paymentIntentId,
            FundsHoldStatus status
    ) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM funds_holds
                        WHERE reference_type = 'PAYMENT_INTENT'
                          AND reference_id = ?
                          AND status = ?
                        """,
                Long.class,
                paymentIntentId,
                status.name()
        );
    }

    private String uniqueSuffix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toLowerCase(Locale.ROOT);
    }

    private record PaymentContext(UUID paymentIntentId) {
    }

    private record WalletContext(
            Customer customer,
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }
}
