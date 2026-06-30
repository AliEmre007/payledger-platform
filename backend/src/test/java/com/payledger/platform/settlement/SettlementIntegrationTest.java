package com.payledger.platform.settlement;

import tools.jackson.databind.ObjectMapper;
import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.kyc.application.KycOperationsService;
import com.payledger.platform.ledger.application.LedgerBalanceService;
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
import com.payledger.platform.payment.application.CreatePaymentIntentCommand;
import com.payledger.platform.payment.application.PaymentIntentService;
import com.payledger.platform.payment.domain.PaymentIntent;
import com.payledger.platform.settlement.api.CreateSettlementBatchRequest;
import com.payledger.platform.settlement.api.ReconcileSettlementRequest;
import com.payledger.platform.settlement.api.ReconciliationCaseResponse;
import com.payledger.platform.settlement.api.SettlementBatchResponse;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
class SettlementIntegrationTest extends PostgresIntegrationTest {

    private static final String OPERATIONS_ACTOR =
            "settlement-test-operations-actor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private KycOperationsService kycOperationsService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerBalanceService ledgerBalanceService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void settlementCreatesBalancedJournalAndClaimsCapturedPayments()
            throws Exception {
        MerchantPaymentContext context = createCapturedPayment(
                "balanced",
                4_000
        );
        String idempotencyKey = "settlement-balanced-" + UUID.randomUUID();

        SettlementBatchResponse response = createSettlementBatch(
                context.merchant().id(),
                "TRY",
                idempotencyKey
        );

        assertThat(response.totalAmountMinor()).isEqualTo(4_000);
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(countSettlementLines(response.id())).isOne();
        assertThat(countPaymentSettlementLines(context.paymentIntent().getId()))
                .isOne();
        assertThat(countJournalPostings(response.journalEntryId())).isEqualTo(2);
        assertThat(countAuditEvents(
                "SETTLEMENT_BATCH_COMPLETED",
                response.id()
        )).isOne();
        assertThat(countOutboxEvents(
                "SETTLEMENT_BATCH_COMPLETED",
                response.id()
        )).isOne();

        LedgerAccount merchantPayable = ledgerAccountRepository
                .findByMerchantIdAndCurrency(context.merchant().id(), "TRY")
                .orElseThrow();
        assertThat(ledgerBalanceService.calculate(merchantPayable.getId())
                .balanceMinor()).isZero();
    }

    @Test
    void settlementRetryReplaysAndNewBatchCannotClaimSameCapture()
            throws Exception {
        MerchantPaymentContext context = createCapturedPayment("idempotent", 2_500);
        String idempotencyKey = "settlement-retry-" + UUID.randomUUID();

        SettlementBatchResponse first = createSettlementBatch(
                context.merchant().id(),
                "TRY",
                idempotencyKey
        );
        SettlementBatchResponse second = createSettlementBatch(
                context.merchant().id(),
                "TRY",
                idempotencyKey
        );

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(countPaymentSettlementLines(context.paymentIntent().getId()))
                .isOne();

        CreateSettlementBatchRequest request =
                new CreateSettlementBatchRequest(
                        context.merchant().id(),
                        "TRY",
                        "Attempt duplicate settlement."
                );

        mockMvc.perform(
                        post("/api/v1/operations/settlements")
                                .header(
                                        "Idempotency-Key",
                                        "settlement-empty-" + UUID.randomUUID()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        OPERATIONS_ACTOR
                                ))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_SETTLEMENT_LINES"));
    }

    @Test
    void reconciliationMatchesExpectedRecordsAndOpensMismatch()
            throws Exception {
        MerchantPaymentContext matchedContext = createCapturedPayment(
                "matched",
                3_300
        );
        SettlementBatchResponse matchedBatch = createSettlementBatch(
                matchedContext.merchant().id(),
                "TRY",
                "settlement-match-" + UUID.randomUUID()
        );

        ReconciliationCaseResponse matched = reconcile(
                matchedBatch.id(),
                "provider-match-" + UUID.randomUUID(),
                3_300,
                "TRY"
        );

        assertThat(matched.status()).isEqualTo("MATCHED");
        assertThat(matched.discrepancyReason()).isNull();

        MerchantPaymentContext mismatchContext = createCapturedPayment(
                "mismatch",
                5_500
        );
        SettlementBatchResponse mismatchBatch = createSettlementBatch(
                mismatchContext.merchant().id(),
                "TRY",
                "settlement-mismatch-" + UUID.randomUUID()
        );

        ReconciliationCaseResponse mismatch = reconcile(
                mismatchBatch.id(),
                "provider-mismatch-" + UUID.randomUUID(),
                5_000,
                "TRY"
        );

        assertThat(mismatch.status()).isEqualTo("OPEN");
        assertThat(mismatch.discrepancyReason()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(countAuditEvents("SETTLEMENT_RECONCILED", mismatch.id()))
                .isOne();
    }

    @Test
    void settlementEndpointRequiresOperationsRole() throws Exception {
        MerchantPaymentContext context = createCapturedPayment("rbac", 1_700);
        CreateSettlementBatchRequest request =
                new CreateSettlementBatchRequest(
                        context.merchant().id(),
                        "TRY",
                        "Settle through operations API."
                );

        mockMvc.perform(
                        post("/api/v1/operations/settlements")
                                .header(
                                        "Idempotency-Key",
                                        "settlement-rbac-" + UUID.randomUUID()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.customerJwt(
                                        "settlement-customer-" + UUID.randomUUID()
                                ))
                )
                .andExpect(status().isForbidden());
    }

    private MerchantPaymentContext createCapturedPayment(
            String label,
            long amountMinor
    ) {
        WalletContext walletContext = createApprovedWallet(label);
        MerchantDetails merchant = createActiveMerchant(label);
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, walletContext.ledgerAccount(), amountMinor + 2_000);

        PaymentIntent authorized = paymentIntentService.authorize(
                new CreatePaymentIntentCommand(
                        walletContext.customer().getId(),
                        "settlement-customer-" + UUID.randomUUID(),
                        walletContext.wallet().getId(),
                        merchant.id(),
                        amountMinor,
                        "TRY",
                        "payment-for-settlement-" + UUID.randomUUID()
                )
        );

        PaymentIntent captured = paymentIntentService.capture(
                authorized.getId(),
                "capture-for-settlement-" + UUID.randomUUID(),
                OPERATIONS_ACTOR,
                "Capture before settlement test."
        );

        return new MerchantPaymentContext(merchant, captured);
    }

    private SettlementBatchResponse createSettlementBatch(
            UUID merchantId,
            String currency,
            String idempotencyKey
    ) throws Exception {
        CreateSettlementBatchRequest request =
                new CreateSettlementBatchRequest(
                        merchantId,
                        currency,
                        "Settle captured merchant payments."
                );

        String content = mockMvc.perform(
                        post("/api/v1/operations/settlements")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        OPERATIONS_ACTOR
                                ))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(content, SettlementBatchResponse.class);
    }

    private ReconciliationCaseResponse reconcile(
            UUID settlementBatchId,
            String providerReference,
            long actualAmountMinor,
            String actualCurrency
    ) throws Exception {
        ReconcileSettlementRequest request = new ReconcileSettlementRequest(
                providerReference,
                actualAmountMinor,
                actualCurrency,
                "Reconcile simulated provider payout."
        );

        String content = mockMvc.perform(
                        post(
                                "/api/v1/operations/settlements/{settlementBatchId}/reconcile",
                                settlementBatchId
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(com.payledger.platform.shared.security.TestJwtSupport.operationsJwt(
                                        OPERATIONS_ACTOR
                                ))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(content, ReconciliationCaseResponse.class);
    }

    private WalletContext createApprovedWallet(String label) {
        Customer customer = createCustomer(label);
        kycOperationsService.submitForReview(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Submit KYC before settlement test."
        );
        kycOperationsService.approve(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Approve KYC before settlement test."
        );
        Wallet wallet = walletService.createWallet(customer.getId(), "TRY");
        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();

        return new WalletContext(customer, wallet, ledgerAccount);
    }

    private MerchantDetails createActiveMerchant(String label) {
        String suffix = uniqueSuffix();
        MerchantDetails merchant = merchantService.onboard(
                new OnboardMerchantCommand(
                        "Settlement Merchant " + label + " " + suffix,
                        "Settlement " + label + " " + suffix,
                        "TRY",
                        0,
                        OPERATIONS_ACTOR,
                        "Onboard merchant for settlement test."
                )
        );

        return merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate merchant for settlement test."
        );
    }

    private Customer createCustomer(String label) {
        String suffix = uniqueSuffix().toLowerCase(Locale.ROOT);

        return customerService.createCustomer(
                CustomerType.PERSONAL,
                "Settlement Customer " + label + " " + suffix,
                "settlement-" + label + "-" + suffix + "@example.test"
        );
    }

    private LedgerAccount createPlatformCashAccount() {
        return ledgerAccountRepository.saveAndFlush(
                LedgerAccount.createPlatformAccount(
                        "SETTLEMENT_TEST_CASH_" + uniqueSuffix(),
                        LedgerAccountType.ASSET,
                        "TRY"
                )
        );
    }

    private void topUp(
            LedgerAccount platformCash,
            LedgerAccount walletAccount,
            long amountMinor
    ) {
        ledgerService.post(
                new PostJournalEntryCommand(
                        "WALLET_TOP_UP",
                        "SETTLEMENT_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a wallet before settlement testing.",
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

    private long countSettlementLines(UUID settlementBatchId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM settlement_lines
                        WHERE settlement_batch_id = ?
                        """,
                Long.class,
                settlementBatchId
        );
    }

    private long countPaymentSettlementLines(UUID paymentIntentId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM settlement_lines
                        WHERE payment_intent_id = ?
                        """,
                Long.class,
                paymentIntentId
        );
    }

    private long countJournalPostings(UUID journalEntryId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM ledger_postings
                        WHERE journal_entry_id = ?
                        """,
                Long.class,
                journalEntryId
        );
    }

    private long countAuditEvents(String actionType, UUID resourceId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM audit_events
                        WHERE action_type = ?
                          AND resource_id = ?
                        """,
                Long.class,
                actionType,
                resourceId
        );
    }

    private long countOutboxEvents(String eventType, UUID aggregateId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM outbox_events
                        WHERE event_type = ?
                          AND aggregate_id = ?
                        """,
                Long.class,
                eventType,
                aggregateId
        );
    }

    private String uniqueSuffix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private record WalletContext(
            Customer customer,
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }

    private record MerchantPaymentContext(
            MerchantDetails merchant,
            PaymentIntent paymentIntent
    ) {
    }
}
