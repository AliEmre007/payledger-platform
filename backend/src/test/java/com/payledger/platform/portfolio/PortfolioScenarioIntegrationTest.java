package com.payledger.platform.portfolio;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
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
import com.payledger.platform.payment.application.CreatePaymentIntentCommand;
import com.payledger.platform.payment.application.PaymentIntentService;
import com.payledger.platform.payment.domain.PaymentIntent;
import com.payledger.platform.payment.domain.PaymentIntentStatus;
import com.payledger.platform.settlement.application.CreateSettlementBatchCommand;
import com.payledger.platform.settlement.application.ReconcileSettlementCommand;
import com.payledger.platform.settlement.application.ReconciliationCaseDetails;
import com.payledger.platform.settlement.application.SettlementBatchDetails;
import com.payledger.platform.settlement.application.SettlementService;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.transfer.application.CreateTransferCommand;
import com.payledger.platform.transfer.application.TransferService;
import com.payledger.platform.transfer.domain.Transfer;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PortfolioScenarioIntegrationTest extends PostgresIntegrationTest {

    private static final String OPERATIONS_ACTOR =
            "portfolio-scenario-operations";

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
    private TransferService transferService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void simulatedPortfolioFlowIsTraceableAcrossLedgerAuditAndOutbox() {
        WalletContext alice = createApprovedWallet("alice");
        WalletContext bob = createApprovedWallet("bob");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, alice.ledgerAccount(), 25_000);

        Transfer transfer = transferService.createCompletedTransfer(
                new CreateTransferCommand(
                        alice.wallet().getId(),
                        bob.wallet().getId(),
                        alice.customer().getId(),
                        "portfolio-alice-subject",
                        4_000,
                        "TRY",
                        "portfolio-transfer-" + UUID.randomUUID()
                )
        );

        assertTraceable(
                "TRANSFER",
                transfer.getId(),
                transfer.getJournalEntryId(),
                "INTERNAL_TRANSFER_COMPLETED",
                "INTERNAL_TRANSFER_COMPLETED"
        );

        MerchantDetails merchant = createActiveMerchant();
        PaymentIntent authorized = paymentIntentService.authorize(
                new CreatePaymentIntentCommand(
                        bob.customer().getId(),
                        "portfolio-bob-subject",
                        bob.wallet().getId(),
                        merchant.id(),
                        2_500,
                        "TRY",
                        "portfolio-payment-auth-" + UUID.randomUUID()
                )
        );

        assertThat(authorized.getStatus())
                .isEqualTo(PaymentIntentStatus.AUTHORIZED);
        assertThat(countActivePaymentHolds(authorized.getId())).isOne();
        assertThat(countJournals("PAYMENT_INTENT", authorized.getId()))
                .isZero();
        assertBusinessEvent("PAYMENT_INTENT_AUTHORIZED",
                "PAYMENT_INTENT", authorized.getId());

        PaymentIntent captured = paymentIntentService.capture(
                authorized.getId(),
                "portfolio-payment-capture-" + UUID.randomUUID(),
                OPERATIONS_ACTOR,
                "Capture demo payment."
        );

        assertTraceable(
                "PAYMENT_INTENT",
                captured.getId(),
                captured.getCaptureJournalEntryId(),
                "PAYMENT_INTENT_CAPTURED",
                "PAYMENT_INTENT_CAPTURED"
        );

        PaymentIntent refunded = paymentIntentService.refund(
                captured.getId(),
                "portfolio-payment-refund-" + UUID.randomUUID(),
                OPERATIONS_ACTOR,
                "Refund demo payment."
        );

        assertTraceable(
                "PAYMENT_INTENT",
                refunded.getId(),
                refunded.getRefundJournalEntryId(),
                "PAYMENT_INTENT_REFUNDED",
                "PAYMENT_INTENT_REFUNDED"
        );

        PaymentIntent settlementPayment = createCapturedPayment(
                "settlement",
                merchant,
                3_000
        );
        SettlementBatchDetails batch = settlementService.createBatch(
                new CreateSettlementBatchCommand(
                        merchant.id(),
                        "TRY",
                        "portfolio-settlement-" + UUID.randomUUID(),
                        OPERATIONS_ACTOR,
                        "Settle demo merchant payment."
                )
        );

        assertThat(batch.totalAmountMinor()).isEqualTo(3_000);
        assertThat(countSettlementLines(batch.id())).isOne();
        assertThat(countSettlementLineForPayment(settlementPayment.getId()))
                .isOne();
        assertThat(countBusinessRecords("SETTLEMENT_BATCH", batch.id()))
                .isOne();
        assertThat(countJournalPostings(batch.journalEntryId())).isEqualTo(2);
        assertBusinessEvent(
                "SETTLEMENT_BATCH_COMPLETED",
                "SETTLEMENT",
                batch.id()
        );

        ReconciliationCaseDetails mismatch = settlementService.reconcile(
                new ReconcileSettlementCommand(
                        batch.id(),
                        "portfolio-provider-payout-" + UUID.randomUUID(),
                        2_990,
                        "TRY",
                        OPERATIONS_ACTOR,
                        "Record simulated provider discrepancy."
                )
        );

        assertThat(mismatch.discrepancyReason())
                .isEqualTo("AMOUNT_MISMATCH");
        assertThat(mismatch.status()).isEqualTo("OPEN");
        assertBusinessEvent(
                "SETTLEMENT_RECONCILED",
                "SETTLEMENT",
                mismatch.id()
        );
    }

    private PaymentIntent createCapturedPayment(
            String label,
            MerchantDetails merchant,
            long amountMinor
    ) {
        WalletContext walletContext = createApprovedWallet(label);
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, walletContext.ledgerAccount(), amountMinor + 1_000);

        PaymentIntent authorized = paymentIntentService.authorize(
                new CreatePaymentIntentCommand(
                        walletContext.customer().getId(),
                        "portfolio-" + label + "-subject",
                        walletContext.wallet().getId(),
                        merchant.id(),
                        amountMinor,
                        "TRY",
                        "portfolio-" + label + "-auth-" + UUID.randomUUID()
                )
        );

        return paymentIntentService.capture(
                authorized.getId(),
                "portfolio-" + label + "-capture-" + UUID.randomUUID(),
                OPERATIONS_ACTOR,
                "Capture payment for " + label + "."
        );
    }

    private WalletContext createApprovedWallet(String label) {
        Customer customer = customerService.createCustomer(
                CustomerType.PERSONAL,
                "Portfolio " + label + " " + uniqueSuffix(),
                "portfolio-" + label + "-" + uniqueSuffix().toLowerCase(Locale.ROOT)
                        + "@example.test"
        );
        kycOperationsService.submitForReview(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Submit demo KYC."
        );
        kycOperationsService.approve(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Approve demo KYC."
        );
        Wallet wallet = walletService.createWallet(customer.getId(), "TRY");
        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();

        return new WalletContext(customer, wallet, ledgerAccount);
    }

    private MerchantDetails createActiveMerchant() {
        MerchantDetails merchant = merchantService.onboard(
                new OnboardMerchantCommand(
                        "Portfolio Merchant " + uniqueSuffix(),
                        "Portfolio Store " + uniqueSuffix(),
                        "TRY",
                        0,
                        OPERATIONS_ACTOR,
                        "Onboard demo merchant."
                )
        );

        return merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate demo merchant."
        );
    }

    private LedgerAccount createPlatformCashAccount() {
        return ledgerAccountRepository.saveAndFlush(
                LedgerAccount.createPlatformAccount(
                        "PORTFOLIO_TEST_CASH_" + uniqueSuffix(),
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
                        "PORTFOLIO_SCENARIO_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a simulated demo wallet.",
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

    private void assertTraceable(
            String referenceType,
            UUID resourceId,
            UUID journalEntryId,
            String auditAction,
            String outboxType
    ) {
        assertThat(countBusinessRecords(referenceType, resourceId)).isOne();
        assertThat(countJournalPostings(journalEntryId)).isEqualTo(2);
        assertBusinessEvent(auditAction, referenceType, resourceId);
        assertThat(countOutboxEvents(outboxType, referenceType, resourceId))
                .isOne();
    }

    private void assertBusinessEvent(
            String actionType,
            String resourceType,
            UUID resourceId
    ) {
        assertThat(countAuditEvents(actionType, resourceType, resourceId))
                .isOne();
        assertThat(countOutboxEvents(actionType, resourceType, resourceId))
                .isOne();
    }

    private long countBusinessRecords(String referenceType, UUID resourceId) {
        return switch (referenceType) {
            case "TRANSFER" -> countRows("transfers", resourceId);
            case "PAYMENT_INTENT" -> countRows("payment_intents", resourceId);
            case "SETTLEMENT_BATCH" -> countRows("settlement_batches", resourceId);
            default -> throw new IllegalArgumentException(referenceType);
        };
    }

    private long countRows(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE id = ?",
                Long.class,
                id
        );
    }

    private long countJournals(String referenceType, UUID referenceId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM journal_entries
                        WHERE reference_type = ?
                          AND reference_id = ?
                        """,
                Long.class,
                referenceType,
                referenceId
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

    private long countAuditEvents(
            String actionType,
            String resourceType,
            UUID resourceId
    ) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM audit_events
                        WHERE action_type = ?
                          AND resource_type = ?
                          AND resource_id = ?
                        """,
                Long.class,
                actionType,
                resourceType,
                resourceId
        );
    }

    private long countOutboxEvents(
            String eventType,
            String aggregateType,
            UUID aggregateId
    ) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM outbox_events
                        WHERE event_type = ?
                          AND aggregate_type = ?
                          AND aggregate_id = ?
                        """,
                Long.class,
                eventType,
                aggregateType,
                aggregateId
        );
    }

    private long countActivePaymentHolds(UUID paymentIntentId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM funds_holds
                        WHERE reference_type = 'PAYMENT_INTENT'
                          AND reference_id = ?
                          AND status = 'ACTIVE'
                        """,
                Long.class,
                paymentIntentId
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

    private long countSettlementLineForPayment(UUID paymentIntentId) {
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
}
