package com.payledger.platform.transfer.application;

import com.payledger.platform.ledger.application.LedgerBalance;
import com.payledger.platform.ledger.application.LedgerBalanceService;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.JournalEntry;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountStatus;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.shared.error.IdempotencyConflictException;
import com.payledger.platform.shared.error.InsufficientFundsException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.transfer.domain.Transfer;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.domain.WalletStatus;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final TransferRepository transferRepository;
    private final LedgerBalanceService ledgerBalanceService;
    private final LedgerService ledgerService;

    public TransferService(
            WalletRepository walletRepository,
            LedgerAccountRepository ledgerAccountRepository,
            TransferRepository transferRepository,
            LedgerBalanceService ledgerBalanceService,
            LedgerService ledgerService
    ) {
        this.walletRepository = walletRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.transferRepository = transferRepository;
        this.ledgerBalanceService = ledgerBalanceService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public Transfer createCompletedTransfer(CreateTransferCommand command) {
        Wallet sourceWallet = getWallet(
                command.sourceWalletId(),
                "Source wallet"
        );

        String fingerprint = TransferRequestFingerprint.calculate(command);

        Optional<Transfer> existingTransfer = transferRepository
                .findBySourceWalletIdAndIdempotencyKey(
                        sourceWallet.getId(),
                        command.idempotencyKey()
                );

        if (existingTransfer.isPresent()) {
            return replayOrReject(existingTransfer.get(), fingerprint);
        }

        Wallet destinationWallet = getWallet(
                command.destinationWalletId(),
                "Destination wallet"
        );

        validateWallets(sourceWallet, destinationWallet, command);

        /*
         * The lock remains held until this transaction commits or rolls back.
         * Every money-moving workflow must use the same locking discipline.
         */
        LedgerAccount sourceLedgerAccount = ledgerAccountRepository
                .findByWalletIdForUpdate(sourceWallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source ledger account not found for wallet: "
                        + sourceWallet.getId()
                ));

        requireActiveLedgerAccount(
                sourceLedgerAccount,
                "Source ledger account"
        );

        /*
         * Another request may have completed with the same key while this
         * request waited for the source-account lock. Check again after lock.
         */
        existingTransfer = transferRepository
                .findBySourceWalletIdAndIdempotencyKey(
                        sourceWallet.getId(),
                        command.idempotencyKey()
                );

        if (existingTransfer.isPresent()) {
            return replayOrReject(existingTransfer.get(), fingerprint);
        }

        LedgerAccount destinationLedgerAccount = ledgerAccountRepository
                .findByWalletId(destinationWallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Destination ledger account not found for wallet: "
                        + destinationWallet.getId()
                ));

        requireActiveLedgerAccount(
                destinationLedgerAccount,
                "Destination ledger account"
        );

        LedgerBalance sourceBalance = ledgerBalanceService.calculate(
                sourceLedgerAccount.getId()
        );

        if (sourceBalance.balanceMinor() < command.amountMinor()) {
            throw new InsufficientFundsException(
                    "Source wallet does not have sufficient available funds."
            );
        }

        UUID transferId = UUID.randomUUID();

        JournalEntry journalEntry = ledgerService.post(
                new PostJournalEntryCommand(
                        "INTERNAL_TRANSFER",
                        "TRANSFER",
                        transferId,
                        command.currency(),
                        "Internal wallet-to-wallet transfer.",
                        Instant.now(),
                        List.of(
                                new LedgerPostingCommand(
                                        sourceLedgerAccount.getId(),
                                        PostingDirection.DEBIT,
                                        command.amountMinor()
                                ),
                                new LedgerPostingCommand(
                                        destinationLedgerAccount.getId(),
                                        PostingDirection.CREDIT,
                                        command.amountMinor()
                                )
                        )
                )
        );

        Transfer transfer = Transfer.completed(
                transferId,
                sourceWallet.getId(),
                destinationWallet.getId(),
                command.initiatedByCustomerId(),
                journalEntry.getId(),
                command.amountMinor(),
                command.currency(),
                command.idempotencyKey(),
                fingerprint
        );

        return transferRepository.saveAndFlush(transfer);
    }

    private Wallet getWallet(UUID walletId, String label) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        label + " not found: " + walletId
                ));
    }

    private Transfer replayOrReject(
            Transfer existingTransfer,
            String fingerprint
    ) {
        if (!existingTransfer.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(
                    "The idempotency key was already used for a different transfer request."
            );
        }

        return existingTransfer;
    }

    private void validateWallets(
            Wallet sourceWallet,
            Wallet destinationWallet,
            CreateTransferCommand command
    ) {
        if (!sourceWallet.getCustomerId().equals(
                command.initiatedByCustomerId()
        )) {
            throw new IllegalArgumentException(
                    "The initiating customer must own the source wallet."
            );
        }

        if (sourceWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Source wallet is not active."
            );
        }

        if (destinationWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Destination wallet is not active."
            );
        }

        if (!sourceWallet.getCurrency().equals(
                destinationWallet.getCurrency()
        )) {
            throw new IllegalArgumentException(
                    "Source and destination wallets must use the same currency."
            );
        }

        if (!sourceWallet.getCurrency().equals(command.currency())) {
            throw new IllegalArgumentException(
                    "Transfer currency must match the source wallet currency."
            );
        }
    }

    private void requireActiveLedgerAccount(
            LedgerAccount ledgerAccount,
            String label
    ) {
        if (ledgerAccount.getStatus() != LedgerAccountStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    label + " is not active."
            );
        }
    }
}
