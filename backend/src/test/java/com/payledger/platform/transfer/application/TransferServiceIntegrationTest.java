package com.payledger.platform.transfer.application;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.CustomerType;
import com.payledger.platform.kyc.application.KycOperationsService;
import com.payledger.platform.ledger.application.LedgerBalance;
import com.payledger.platform.ledger.application.LedgerBalanceService;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.JournalEntryRepository;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.shared.error.IdempotencyConflictException;
import com.payledger.platform.shared.error.InsufficientFundsException;
import com.payledger.platform.support.PostgresIntegrationTest;
import com.payledger.platform.transfer.domain.Transfer;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.application.WalletService;
import com.payledger.platform.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TransferServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private KycOperationsService kycOperationsService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerBalanceService ledgerBalanceService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void completesTransferAndMovesLedgerBackedFunds() {
        WalletContext sender = createTryWallet("sender");
        WalletContext recipient = createTryWallet("recipient");

        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);

        Transfer transfer = transferService.createCompletedTransfer(
                transferCommand(
                        sender,
                        recipient,
                        2_500,
                        "transfer-success-001"
                )
        );

        LedgerBalance senderBalance = ledgerBalanceService.calculate(
                sender.ledgerAccount().getId()
        );

        LedgerBalance recipientBalance = ledgerBalanceService.calculate(
                recipient.ledgerAccount().getId()
        );

        assertThat(transfer.getId()).isNotNull();
        assertThat(transfer.getAmountMinor()).isEqualTo(2_500);
        assertThat(transfer.getCurrency()).isEqualTo("TRY");
        assertThat(transferRepository.findById(transfer.getId())).isPresent();
        assertThat(journalEntryRepository.existsById(
                transfer.getJournalEntryId()
        )).isTrue();

        assertThat(senderBalance.balanceMinor()).isEqualTo(7_500);
        assertThat(recipientBalance.balanceMinor()).isEqualTo(2_500);
    }

    @Test
    void rejectsTransferWhenSourceBalanceIsInsufficient() {
        WalletContext sender = createTryWallet("sender");
        WalletContext recipient = createTryWallet("recipient");

        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 1_000);

        CreateTransferCommand command = transferCommand(
                sender,
                recipient,
                1_001,
                "transfer-insufficient-funds-001"
        );

        assertThatThrownBy(() ->
                transferService.createCompletedTransfer(command)
        )
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("sufficient");

        assertThat(
                transferRepository.countBySourceWalletIdAndIdempotencyKey(
                        sender.wallet().getId(),
                        command.idempotencyKey()
                )
        ).isZero();

        assertThat(
                ledgerBalanceService.calculate(
                        sender.ledgerAccount().getId()
                ).balanceMinor()
        ).isEqualTo(1_000);

        assertThat(
                ledgerBalanceService.calculate(
                        recipient.ledgerAccount().getId()
                ).balanceMinor()
        ).isZero();
    }

    @Test
    void replaysExistingTransferForSameIdempotencyKeyAndRequest() {
        WalletContext sender = createTryWallet("sender");
        WalletContext recipient = createTryWallet("recipient");

        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);

        CreateTransferCommand command = transferCommand(
                sender,
                recipient,
                3_000,
                "transfer-replay-001"
        );

        Transfer first = transferService.createCompletedTransfer(command);
        Transfer replay = transferService.createCompletedTransfer(command);

        assertThat(replay.getId()).isEqualTo(first.getId());

        assertThat(
                transferRepository.countBySourceWalletIdAndIdempotencyKey(
                        sender.wallet().getId(),
                        command.idempotencyKey()
                )
        ).isEqualTo(1);

        assertThat(
                ledgerBalanceService.calculate(
                        sender.ledgerAccount().getId()
                ).balanceMinor()
        ).isEqualTo(7_000);

        assertThat(
                ledgerBalanceService.calculate(
                        recipient.ledgerAccount().getId()
                ).balanceMinor()
        ).isEqualTo(3_000);
    }

    @Test
    void rejectsSameIdempotencyKeyWhenRequestPayloadChanges() {
        WalletContext sender = createTryWallet("sender");
        WalletContext recipient = createTryWallet("recipient");

        LedgerAccount platformCash = createPlatformCashAccount();
        topUp(platformCash, sender.ledgerAccount(), 10_000);

        String idempotencyKey = "transfer-conflict-001";

        Transfer original = transferService.createCompletedTransfer(
                transferCommand(
                        sender,
                        recipient,
                        1_000,
                        idempotencyKey
                )
        );

        assertThatThrownBy(() ->
                transferService.createCompletedTransfer(
                        transferCommand(
                                sender,
                                recipient,
                                2_000,
                                idempotencyKey
                        )
                )
        )
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different transfer request");

        assertThat(
                transferRepository.countBySourceWalletIdAndIdempotencyKey(
                        sender.wallet().getId(),
                        idempotencyKey
                )
        ).isEqualTo(1);

        assertThat(
                ledgerBalanceService.calculate(
                        sender.ledgerAccount().getId()
                ).balanceMinor()
        ).isEqualTo(9_000);

        assertThat(
                ledgerBalanceService.calculate(
                        recipient.ledgerAccount().getId()
                ).balanceMinor()
        ).isEqualTo(1_000);

        assertThat(original.getAmountMinor()).isEqualTo(1_000);
    }

    private WalletContext createTryWallet(String label) {
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
        String actor = "kyc-transfer-test-actor-" + UUID.randomUUID();
        kycOperationsService.submitForReview(
                customer.getId(),
                actor,
                "Prepare customer for transfer service testing."
        );
        kycOperationsService.approve(
                customer.getId(),
                actor,
                "Approve customer for transfer service testing."
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
                        "TEST_PLATFORM_CASH_" + suffix,
                        LedgerAccountType.ASSET,
                        "TRY"
                )
        );
    }

    private void topUp(
            LedgerAccount platformCash,
            LedgerAccount customerWalletAccount,
            long amountMinor
    ) {
        ledgerService.post(
                new PostJournalEntryCommand(
                        "WALLET_TOP_UP",
                        "TRANSFER_TEST_TOP_UP",
                        UUID.randomUUID(),
                        "TRY",
                        "Fund a test wallet before transfer execution.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        platformCash.getId(),
                                        PostingDirection.DEBIT,
                                        amountMinor
                                ),
                                new LedgerPostingCommand(
                                        customerWalletAccount.getId(),
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
            String idempotencyKey
    ) {
        return new CreateTransferCommand(
                sender.wallet().getId(),
                recipient.wallet().getId(),
                sender.customer().getId(),
                "transfer-service-subject-" + UUID.randomUUID(),
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
