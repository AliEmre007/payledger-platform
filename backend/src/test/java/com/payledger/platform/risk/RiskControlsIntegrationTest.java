package com.payledger.platform.risk;

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
import com.payledger.platform.payment.infrastructure.PaymentIntentRepository;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.transfer.application.CreateTransferCommand;
import com.payledger.platform.transfer.application.TransferService;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RiskControlsIntegrationTest extends PostgresIntegrationTest {

    private static final String OPERATIONS_ACTOR =
            "risk-controls-test-operations-actor";

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
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void transferAmountLimitRejectsBeforeLedgerPosting() {
        WalletContext sender = createApprovedWallet("transfer-limit-sender");
        WalletContext recipient = createApprovedWallet("transfer-limit-recipient");
        topUp(sender.ledgerAccount(), 700_000);
        String actor = "risk-transfer-limit-" + UUID.randomUUID();
        String idempotencyKey = "risk-transfer-limit-" + UUID.randomUUID();

        assertThatThrownBy(() ->
                transferService.createCompletedTransfer(
                        transferCommand(
                                sender,
                                recipient,
                                500_001,
                                idempotencyKey,
                                actor
                        )
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("RISK_DENIED")
                );

        assertThat(
                transferRepository.countBySourceWalletIdAndIdempotencyKey(
                        sender.wallet().getId(),
                        idempotencyKey
                )
        ).isZero();
        assertThat(ledgerBalanceService.calculate(sender.ledgerAccount().getId())
                .balanceMinor()).isEqualTo(700_000);
        assertRiskDenial(actor, "AMOUNT_LIMIT_EXCEEDED");
    }

    @Test
    void paymentAmountLimitRejectsBeforeHoldCreation() {
        WalletContext walletContext = createApprovedWallet("payment-limit");
        MerchantDetails merchant = createActiveMerchant("payment-limit");
        topUp(walletContext.ledgerAccount(), 700_000);
        String actor = "risk-payment-limit-" + UUID.randomUUID();
        String idempotencyKey = "risk-payment-limit-" + UUID.randomUUID();

        assertThatThrownBy(() ->
                paymentIntentService.authorize(
                        new CreatePaymentIntentCommand(
                                walletContext.customer().getId(),
                                actor,
                                walletContext.wallet().getId(),
                                merchant.id(),
                                500_001,
                                "TRY",
                                idempotencyKey
                        )
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("RISK_DENIED")
                );

        assertThat(paymentIntentRepository.findByCustomerIdAndIdempotencyKey(
                walletContext.customer().getId(),
                idempotencyKey
        )).isEmpty();
        assertThat(countFundsHolds(walletContext.wallet().getId())).isZero();
        assertRiskDenial(actor, "AMOUNT_LIMIT_EXCEEDED");
    }

    @Test
    void dailyOutgoingAmountVelocityRejectsAcrossCompletedTransfers() {
        WalletContext sender = createApprovedWallet("daily-amount-sender");
        WalletContext recipient = createApprovedWallet("daily-amount-recipient");
        topUp(sender.ledgerAccount(), 1_200_000);
        String actor = "risk-daily-amount-" + UUID.randomUUID();

        transferService.createCompletedTransfer(
                transferCommand(
                        sender,
                        recipient,
                        400_000,
                        "risk-daily-amount-a-" + UUID.randomUUID(),
                        actor
                )
        );
        transferService.createCompletedTransfer(
                transferCommand(
                        sender,
                        recipient,
                        400_000,
                        "risk-daily-amount-b-" + UUID.randomUUID(),
                        actor
                )
        );

        assertThatThrownBy(() ->
                transferService.createCompletedTransfer(
                        transferCommand(
                                sender,
                                recipient,
                                250_000,
                                "risk-daily-amount-c-" + UUID.randomUUID(),
                                actor
                        )
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("RISK_DENIED")
                );

        assertRiskDenial(actor, "DAILY_AMOUNT_LIMIT_EXCEEDED");
        assertThat(ledgerBalanceService.calculate(sender.ledgerAccount().getId())
                .balanceMinor()).isEqualTo(400_000);
    }

    @Test
    void dailyOutgoingCountVelocityRejectsSixthCommand() {
        WalletContext sender = createApprovedWallet("daily-count-sender");
        WalletContext recipient = createApprovedWallet("daily-count-recipient");
        topUp(sender.ledgerAccount(), 20_000);
        String actor = "risk-daily-count-" + UUID.randomUUID();

        for (int index = 0; index < 5; index++) {
            transferService.createCompletedTransfer(
                    transferCommand(
                            sender,
                            recipient,
                            1_000,
                            "risk-daily-count-" + index + "-" + UUID.randomUUID(),
                            actor
                    )
            );
        }

        assertThatThrownBy(() ->
                transferService.createCompletedTransfer(
                        transferCommand(
                                sender,
                                recipient,
                                1_000,
                                "risk-daily-count-denied-" + UUID.randomUUID(),
                                actor
                        )
                )
        )
                .isInstanceOfSatisfying(
                        BusinessRuleViolationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("RISK_DENIED")
                );

        assertRiskDenial(actor, "DAILY_COUNT_LIMIT_EXCEEDED");
        assertThat(ledgerBalanceService.calculate(sender.ledgerAccount().getId())
                .balanceMinor()).isEqualTo(15_000);
    }

    @Test
    void normalTransferPathRemainsAllowed() {
        WalletContext sender = createApprovedWallet("normal-sender");
        WalletContext recipient = createApprovedWallet("normal-recipient");
        topUp(sender.ledgerAccount(), 10_000);

        transferService.createCompletedTransfer(
                transferCommand(
                        sender,
                        recipient,
                        2_500,
                        "risk-normal-transfer-" + UUID.randomUUID(),
                        "risk-normal-actor-" + UUID.randomUUID()
                )
        );

        assertThat(ledgerBalanceService.calculate(sender.ledgerAccount().getId())
                .balanceMinor()).isEqualTo(7_500);
        assertThat(ledgerBalanceService.calculate(
                recipient.ledgerAccount().getId()
        ).balanceMinor()).isEqualTo(2_500);
    }

    private WalletContext createApprovedWallet(String label) {
        Customer customer = createCustomer(label);
        kycOperationsService.submitForReview(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Submit KYC for risk controls test."
        );
        kycOperationsService.approve(
                customer.getId(),
                OPERATIONS_ACTOR,
                "Approve KYC for risk controls test."
        );
        Wallet wallet = walletService.createWallet(customer.getId(), "TRY");
        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();

        return new WalletContext(customer, wallet, ledgerAccount);
    }

    private Customer createCustomer(String label) {
        String suffix = uniqueSuffix().toLowerCase(Locale.ROOT);

        return customerService.createCustomer(
                CustomerType.PERSONAL,
                "Risk Customer " + label + " " + suffix,
                "risk-" + label + "-" + suffix + "@example.test"
        );
    }

    private MerchantDetails createActiveMerchant(String label) {
        String suffix = uniqueSuffix();
        MerchantDetails merchant = merchantService.onboard(
                new OnboardMerchantCommand(
                        "Risk Merchant " + label + " " + suffix,
                        "Risk " + label + " " + suffix,
                        "TRY",
                        0,
                        OPERATIONS_ACTOR,
                        "Onboard merchant for risk controls test."
                )
        );

        return merchantService.activate(
                merchant.id(),
                OPERATIONS_ACTOR,
                "Activate merchant for risk controls test."
        );
    }

    private void topUp(LedgerAccount walletAccount, long amountMinor) {
        LedgerAccount platformCash = ledgerAccountRepository.saveAndFlush(
                LedgerAccount.createPlatformAccount(
                        "RISK_TEST_CASH_" + uniqueSuffix(),
                        LedgerAccountType.ASSET,
                        "TRY"
                )
        );

        ledgerService.post(
                new PostJournalEntryCommand(
                        "WALLET_TOP_UP",
                        "RISK_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a wallet before risk controls testing.",
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

    private CreateTransferCommand transferCommand(
            WalletContext sender,
            WalletContext recipient,
            long amountMinor,
            String idempotencyKey,
            String actor
    ) {
        return new CreateTransferCommand(
                sender.wallet().getId(),
                recipient.wallet().getId(),
                sender.customer().getId(),
                actor,
                amountMinor,
                "TRY",
                idempotencyKey
        );
    }

    private long countFundsHolds(UUID walletId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM funds_holds
                        WHERE wallet_id = ?
                        """,
                Long.class,
                walletId
        );
    }

    private void assertRiskDenial(String actor, String reasonCode) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM audit_events
                        WHERE action_type = 'RISK_DECISION_DENIED'
                          AND actor_external_subject = ?
                          AND metadata ->> 'reasonCode' = ?
                        """,
                Long.class,
                actor,
                reasonCode
        );

        assertThat(count).isOne();
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
