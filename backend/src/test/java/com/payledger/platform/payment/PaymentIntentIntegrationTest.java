package com.payledger.platform.payment;

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
import com.payledger.platform.payment.infrastructure.PaymentIntentRepository;
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
class PaymentIntentIntegrationTest extends PostgresIntegrationTest {

    private static final String OPERATIONS_ACTOR =
            "payment-intent-test-operations-actor";

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
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void authorizationCreatesHoldAndNoPaymentLedgerPosting()
            throws Exception {
        WalletContext walletContext = createApprovedWallet("authorize", "TRY");
        String subject = linkSubject(walletContext.customer());
        MerchantDetails merchant = createActiveMerchant("authorize", "TRY");
        topUp(walletContext.ledgerAccount(), 10_000, "TRY");

        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest(
                walletContext.wallet().getId(),
                merchant.id(),
                2_500,
                "TRY"
        );

        mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header(
                                        "Idempotency-Key",
                                        "payment-auth-" + UUID.randomUUID()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(subject))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.sourceWalletId")
                        .value(walletContext.wallet().getId().toString()))
                .andExpect(jsonPath("$.merchantId")
                        .value(merchant.id().toString()))
                .andExpect(jsonPath("$.fundsHoldId").isNotEmpty())
                .andExpect(jsonPath("$.amountMinor").value(2_500))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.status")
                        .value(PaymentIntentStatus.AUTHORIZED.name()));

        UUID paymentIntentId = paymentIntentRepository.findAll()
                .stream()
                .filter(intent -> intent.getMerchantId().equals(merchant.id()))
                .findFirst()
                .orElseThrow()
                .getId();

        assertThat(countFundsHolds(paymentIntentId, FundsHoldStatus.ACTIVE))
                .isOne();
        assertThat(countPaymentJournalEntries(paymentIntentId)).isZero();
        assertThat(countAuditEvents(
                "PAYMENT_INTENT_AUTHORIZED",
                paymentIntentId
        )).isOne();
        assertThat(countOutboxEvents(
                "PAYMENT_INTENT_AUTHORIZED",
                paymentIntentId
        )).isOne();
    }

    @Test
    void retriedAuthorizationWithSameIdempotencyKeyReplaysSafely()
            throws Exception {
        WalletContext walletContext = createApprovedWallet("retry", "TRY");
        String subject = linkSubject(walletContext.customer());
        MerchantDetails merchant = createActiveMerchant("retry", "TRY");
        topUp(walletContext.ledgerAccount(), 10_000, "TRY");
        String idempotencyKey = "payment-retry-" + UUID.randomUUID();

        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest(
                walletContext.wallet().getId(),
                merchant.id(),
                3_000,
                "TRY"
        );

        String firstResponse = mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(subject))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(subject))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID firstId = objectMapper.readValue(
                firstResponse,
                PaymentIntentResponse.class
        ).id();
        UUID secondId = objectMapper.readValue(
                secondResponse,
                PaymentIntentResponse.class
        ).id();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(countPaymentIntents(walletContext.customer().getId(),
                idempotencyKey)).isOne();
        assertThat(countFundsHolds(firstId, FundsHoldStatus.ACTIVE)).isOne();
        assertThat(countAuditEvents("PAYMENT_INTENT_AUTHORIZED", firstId))
                .isOne();
    }

    @Test
    void insufficientAvailableFundsRejectsAuthorization()
            throws Exception {
        WalletContext walletContext = createApprovedWallet("insufficient", "TRY");
        String subject = linkSubject(walletContext.customer());
        MerchantDetails merchant = createActiveMerchant("insufficient", "TRY");
        topUp(walletContext.ledgerAccount(), 1_000, "TRY");
        String idempotencyKey = "payment-insufficient-" + UUID.randomUUID();

        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest(
                walletContext.wallet().getId(),
                merchant.id(),
                1_500,
                "TRY"
        );

        mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(subject))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(countPaymentIntents(walletContext.customer().getId(),
                idempotencyKey)).isZero();
    }

    @Test
    void rejectsInactiveMerchantWrongOwnerWrongCurrencyAndUnverifiedKyc()
            throws Exception {
        WalletContext owner = createApprovedWallet("owner", "TRY");
        WalletContext requester = createApprovedWallet("requester", "TRY");
        String requesterSubject = linkSubject(requester.customer());
        String ownerSubject = linkSubject(owner.customer());
        MerchantDetails inactiveMerchant = createPendingMerchant(
                "inactive",
                "TRY"
        );
        MerchantDetails usdMerchant = createActiveMerchant(
                "wrong-currency",
                "USD"
        );
        topUp(owner.ledgerAccount(), 10_000, "TRY");
        topUp(requester.ledgerAccount(), 10_000, "TRY");

        CreatePaymentIntentRequest inactiveMerchantRequest =
                new CreatePaymentIntentRequest(
                        owner.wallet().getId(),
                        inactiveMerchant.id(),
                        1_000,
                        "TRY"
                );

        mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header(
                                        "Idempotency-Key",
                                        "payment-inactive-" + UUID.randomUUID()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        inactiveMerchantRequest
                                ))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(ownerSubject))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_ACTIVE"));

        CreatePaymentIntentRequest wrongOwnerRequest =
                new CreatePaymentIntentRequest(
                        owner.wallet().getId(),
                        createActiveMerchant("wrong-owner", "TRY").id(),
                        1_000,
                        "TRY"
                );

        mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header(
                                        "Idempotency-Key",
                                        "payment-wrong-owner-" + UUID.randomUUID()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        wrongOwnerRequest
                                ))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(requesterSubject))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WALLET_ACCESS_DENIED"));

        CreatePaymentIntentRequest wrongCurrencyRequest =
                new CreatePaymentIntentRequest(
                        owner.wallet().getId(),
                        usdMerchant.id(),
                        1_000,
                        "TRY"
                );

        mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header(
                                        "Idempotency-Key",
                                        "payment-wrong-currency-" + UUID.randomUUID()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        wrongCurrencyRequest
                                ))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(ownerSubject))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code")
                        .value("MERCHANT_CURRENCY_NOT_ENABLED"));

        WalletContext unverified = createUnverifiedWallet("unverified", "TRY");
        String unverifiedSubject = linkSubject(unverified.customer());
        topUp(unverified.ledgerAccount(), 10_000, "TRY");

        CreatePaymentIntentRequest unverifiedRequest =
                new CreatePaymentIntentRequest(
                        unverified.wallet().getId(),
                        createActiveMerchant("unverified", "TRY").id(),
                        1_000,
                        "TRY"
                );

        mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header(
                                        "Idempotency-Key",
                                        "payment-unverified-" + UUID.randomUUID()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        unverifiedRequest
                                ))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(unverifiedSubject))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("KYC_NOT_VERIFIED"));
    }

    @Test
    void cancellationReleasesHoldAndIsIdempotent() throws Exception {
        WalletContext walletContext = createApprovedWallet("cancel", "TRY");
        String subject = linkSubject(walletContext.customer());
        MerchantDetails merchant = createActiveMerchant("cancel", "TRY");
        topUp(walletContext.ledgerAccount(), 10_000, "TRY");
        String idempotencyKey = "payment-cancel-" + UUID.randomUUID();

        CreatePaymentIntentRequest request = new CreatePaymentIntentRequest(
                walletContext.wallet().getId(),
                merchant.id(),
                2_000,
                "TRY"
        );

        String response = mockMvc.perform(
                        post("/api/v1/payment-intents")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(subject))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID paymentIntentId = objectMapper.readValue(
                response,
                PaymentIntentResponse.class
        ).id();

        mockMvc.perform(
                        post(
                                "/api/v1/payment-intents/{paymentIntentId}/cancel",
                                paymentIntentId
                        )
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(subject))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value(PaymentIntentStatus.CANCELED.name()));

        mockMvc.perform(
                        post(
                                "/api/v1/payment-intents/{paymentIntentId}/cancel",
                                paymentIntentId
                        )
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(subject))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value(PaymentIntentStatus.CANCELED.name()));

        assertThat(countFundsHolds(paymentIntentId, FundsHoldStatus.RELEASED))
                .isOne();
        assertThat(countAuditEvents("PAYMENT_INTENT_CANCELED",
                paymentIntentId)).isOne();
        assertThat(countOutboxEvents("PAYMENT_INTENT_CANCELED",
                paymentIntentId)).isOne();
    }

    private WalletContext createApprovedWallet(String label, String currency) {
        WalletContext walletContext = createUnverifiedWallet(label, currency);
        approveKyc(walletContext.customer());
        return walletContext;
    }

    private WalletContext createUnverifiedWallet(
            String label,
            String currency
    ) {
        String suffix = uniqueSuffix();
        Customer customer = customerService.createCustomer(
                CustomerType.PERSONAL,
                "Test " + label + " " + suffix,
                label + "-" + suffix + "@example.test"
        );
        Wallet wallet = walletService.createWallet(customer.getId(), currency);
        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();

        return new WalletContext(customer, wallet, ledgerAccount);
    }

    private void approveKyc(Customer customer) {
        String actor = "kyc-payment-intent-test-actor-" + UUID.randomUUID();
        kycOperationsService.submitForReview(
                customer.getId(),
                actor,
                "Prepare customer for payment intent testing."
        );
        kycOperationsService.approve(
                customer.getId(),
                actor,
                "Approve customer for payment intent testing."
        );
    }

    private String linkSubject(Customer customer) {
        String subject = "payment-intent-subject-" + UUID.randomUUID();
        customerIdentityService.linkKeycloakIdentity(customer.getId(), subject);
        return subject;
    }

    private MerchantDetails createPendingMerchant(
            String label,
            String currency
    ) {
        return merchantService.onboard(
                new OnboardMerchantCommand(
                        "Test Merchant " + label + " " + uniqueSuffix(),
                        "Merchant " + label + " " + uniqueSuffix(),
                        currency,
                        1,
                        OPERATIONS_ACTOR,
                        "Onboard merchant for payment intent testing."
                )
        );
    }

    private MerchantDetails createActiveMerchant(
            String label,
            String currency
    ) {
        MerchantDetails merchant = createPendingMerchant(label, currency);
        return merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate merchant for payment intent testing."
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
                        "PAYMENT_INTENT_TEST_TOP_UP",
                        UUID.randomUUID(),
                        currency,
                        "Fund a wallet before payment intent testing.",
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
                        "PAYMENT_INTENT_TEST_CASH_" + suffix,
                        LedgerAccountType.ASSET,
                        currency
                )
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

    private long countPaymentJournalEntries(UUID paymentIntentId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM journal_entries
                        WHERE reference_type = 'PAYMENT_INTENT'
                          AND reference_id = ?
                        """,
                Long.class,
                paymentIntentId
        );
    }

    private long countPaymentIntents(
            UUID customerId,
            String idempotencyKey
    ) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM payment_intents
                        WHERE customer_id = ?
                          AND idempotency_key = ?
                        """,
                Long.class,
                customerId,
                idempotencyKey
        );
    }

    private long countAuditEvents(String actionType, UUID paymentIntentId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM audit_events
                        WHERE action_type = ?
                          AND resource_type = 'PAYMENT_INTENT'
                          AND resource_id = ?
                        """,
                Long.class,
                actionType,
                paymentIntentId
        );
    }

    private long countOutboxEvents(String eventType, UUID paymentIntentId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM outbox_events
                        WHERE event_type = ?
                          AND aggregate_type = 'PAYMENT_INTENT'
                          AND aggregate_id = ?
                        """,
                Long.class,
                eventType,
                paymentIntentId
        );
    }

    private String uniqueSuffix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toLowerCase(Locale.ROOT);
    }

    private record WalletContext(
            Customer customer,
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }
}
