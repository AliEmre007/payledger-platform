package com.payledger.platform.wallet.application;

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
import com.payledger.platform.shared.error.InsufficientFundsException;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.transfer.application.CreateTransferCommand;
import com.payledger.platform.transfer.application.TransferService;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.domain.FundsHold;
import com.payledger.platform.wallet.domain.FundsHoldStatus;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FundsHoldIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private KycOperationsService kycOperationsService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private FundsHoldService fundsHoldService;

    @Autowired
    private WalletAvailableBalanceService availableBalanceService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void holdLowersAvailableBalanceButNotLedgerBalance() {
        WalletContext walletContext = createApprovedWallet("hold-balance");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, walletContext.ledgerAccount(), 10_000);

        fundsHoldService.create(holdCommand(
                walletContext.wallet(),
                3_000,
                "BALANCE_TEST"
        ));

        WalletAvailableBalance balance = availableBalanceService.calculate(
                walletContext.wallet(),
                walletContext.ledgerAccount()
        );

        assertThat(balance.ledgerBalanceMinor()).isEqualTo(10_000);
        assertThat(balance.heldAmountMinor()).isEqualTo(3_000);
        assertThat(balance.availableBalanceMinor()).isEqualTo(7_000);
    }

    @Test
    void transferCannotSpendHeldMoney() {
        WalletContext sender = createApprovedWallet("hold-transfer-sender");
        WalletContext recipient = createApprovedWallet("hold-transfer-recipient");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);
        fundsHoldService.create(holdCommand(
                sender.wallet(),
                8_000,
                "TRANSFER_BLOCK_TEST"
        ));

        CreateTransferCommand command = transferCommand(
                sender,
                recipient,
                3_000,
                "held-transfer-" + UUID.randomUUID()
        );

        assertThatThrownBy(() ->
                transferService.createCompletedTransfer(command)
        )
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("available");

        assertThat(
                transferRepository.countBySourceWalletIdAndIdempotencyKey(
                        sender.wallet().getId(),
                        command.idempotencyKey()
                )
        ).isZero();
    }

    @Test
    void releasingHoldRestoresAvailableBalance() {
        WalletContext walletContext = createApprovedWallet("hold-release");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, walletContext.ledgerAccount(), 10_000);
        FundsHold hold = fundsHoldService.create(holdCommand(
                walletContext.wallet(),
                4_000,
                "RELEASE_TEST"
        ));

        fundsHoldService.release(hold.getId());

        WalletAvailableBalance balance = availableBalanceService.calculate(
                walletContext.wallet(),
                walletContext.ledgerAccount()
        );

        assertThat(balance.ledgerBalanceMinor()).isEqualTo(10_000);
        assertThat(balance.heldAmountMinor()).isZero();
        assertThat(balance.availableBalanceMinor()).isEqualTo(10_000);
    }

    @Test
    void releaseAndCaptureAreIdempotentForSameTerminalAction() {
        WalletContext releasedWallet = createApprovedWallet("hold-release-safe");
        WalletContext capturedWallet = createApprovedWallet("hold-capture-safe");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, releasedWallet.ledgerAccount(), 10_000);
        topUp(platformCash, capturedWallet.ledgerAccount(), 10_000);

        FundsHold released = fundsHoldService.create(holdCommand(
                releasedWallet.wallet(),
                1_000,
                "RELEASE_IDEMPOTENT_TEST"
        ));
        FundsHold captured = fundsHoldService.create(holdCommand(
                capturedWallet.wallet(),
                1_000,
                "CAPTURE_IDEMPOTENT_TEST"
        ));

        FundsHold firstRelease = fundsHoldService.release(released.getId());
        FundsHold secondRelease = fundsHoldService.release(released.getId());
        FundsHold firstCapture = fundsHoldService.capture(captured.getId());
        FundsHold secondCapture = fundsHoldService.capture(captured.getId());

        assertThat(firstRelease.getStatus()).isEqualTo(FundsHoldStatus.RELEASED);
        assertThat(secondRelease.getStatus()).isEqualTo(FundsHoldStatus.RELEASED);
        assertThat(firstCapture.getStatus()).isEqualTo(FundsHoldStatus.CAPTURED);
        assertThat(secondCapture.getStatus()).isEqualTo(FundsHoldStatus.CAPTURED);
    }

    @Test
    void concurrentHoldCreationCannotOverReserveFunds() throws Exception {
        WalletContext walletContext = createApprovedWallet("hold-concurrent");
        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, walletContext.ledgerAccount(), 1_000);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(
                    () -> attemptHoldAfterStart(
                            start,
                            walletContext.wallet(),
                            700,
                            "CONCURRENT_HOLD_A"
                    ),
                    executorService
            );
            CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(
                    () -> attemptHoldAfterStart(
                            start,
                            walletContext.wallet(),
                            700,
                            "CONCURRENT_HOLD_B"
                    ),
                    executorService
            );

            start.countDown();

            boolean firstSucceeded = first.get(10, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstSucceeded, secondSucceeded))
                    .containsExactlyInAnyOrder(true, false);

            WalletAvailableBalance balance = availableBalanceService.calculate(
                    walletContext.wallet(),
                    walletContext.ledgerAccount()
            );

            assertThat(balance.ledgerBalanceMinor()).isEqualTo(1_000);
            assertThat(balance.heldAmountMinor()).isEqualTo(700);
            assertThat(balance.availableBalanceMinor()).isEqualTo(300);
        } finally {
            executorService.shutdownNow();
        }
    }

    private boolean attemptHoldAfterStart(
            CountDownLatch start,
            Wallet wallet,
            long amountMinor,
            String referenceType
    ) {
        try {
            start.await(10, TimeUnit.SECONDS);
            fundsHoldService.create(holdCommand(
                    wallet,
                    amountMinor,
                    referenceType
            ));
            return true;
        } catch (InsufficientFundsException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private WalletContext createApprovedWallet(String label) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toLowerCase(Locale.ROOT);

        Customer customer = customerService.createCustomer(
                CustomerType.PERSONAL,
                "Test " + label + " " + suffix,
                label + "-" + suffix + "@example.test"
        );
        approveKyc(customer);

        Wallet wallet = walletService.createWallet(customer.getId(), "TRY");
        LedgerAccount ledgerAccount = ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseThrow();

        return new WalletContext(customer, wallet, ledgerAccount);
    }

    private void approveKyc(Customer customer) {
        String actor = "kyc-holds-test-actor-" + UUID.randomUUID();
        kycOperationsService.submitForReview(
                customer.getId(),
                actor,
                "Prepare customer for holds testing."
        );
        kycOperationsService.approve(
                customer.getId(),
                actor,
                "Approve customer for holds testing."
        );
    }

    private LedgerAccount createPlatformCashAccount() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);

        return ledgerAccountRepository.saveAndFlush(
                LedgerAccount.createPlatformAccount(
                        "HOLDS_TEST_CASH_" + suffix,
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
                        "FUNDS_HOLD_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a wallet before holds testing.",
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

    private CreateFundsHoldCommand holdCommand(
            Wallet wallet,
            long amountMinor,
            String referenceType
    ) {
        return new CreateFundsHoldCommand(
                wallet.getId(),
                amountMinor,
                "TRY",
                "Reserve funds for test.",
                referenceType,
                UUID.randomUUID(),
                "hold-" + UUID.randomUUID()
        );
    }

    private CreateTransferCommand transferCommand(
            WalletContext sender,
            WalletContext recipient,
            long amountMinor,
            String idempotencyKey
    ) {
        return new CreateTransferCommand(
                sender.wallet().getId(),
                recipient.wallet().getId(),
                sender.customer().getId(),
                "holds-transfer-subject-" + UUID.randomUUID(),
                amountMinor,
                "TRY",
                idempotencyKey
        );
    }

    private record WalletContext(
            Customer customer,
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
    }
}
