package com.payledger.platform.transfer.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.customer.domain.KycStatus;
import com.payledger.platform.customer.infrastructure.CustomerRepository;
import com.payledger.platform.ledger.application.LedgerPostingCommand;
import com.payledger.platform.ledger.application.LedgerService;
import com.payledger.platform.ledger.application.PostJournalEntryCommand;
import com.payledger.platform.ledger.domain.JournalEntry;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountStatus;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.outbox.application.OutboxEventCommand;
import com.payledger.platform.outbox.application.OutboxEventService;
import com.payledger.platform.risk.application.RiskDecision;
import com.payledger.platform.risk.application.RiskDecisionService;
import com.payledger.platform.risk.application.TransferRiskRequest;
import com.payledger.platform.shared.error.BusinessRuleViolationException;
import com.payledger.platform.shared.error.IdempotencyConflictException;
import com.payledger.platform.shared.error.InsufficientFundsException;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.shared.error.WalletAccessDeniedException;
import com.payledger.platform.transfer.domain.Transfer;
import com.payledger.platform.transfer.infrastructure.TransferRepository;
import com.payledger.platform.wallet.application.WalletAvailableBalance;
import com.payledger.platform.wallet.application.WalletAvailableBalanceService;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.domain.WalletStatus;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final CustomerRepository customerRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final TransferRepository transferRepository;
    private final WalletAvailableBalanceService availableBalanceService;
    private final LedgerService ledgerService;
    private final AuditEventService auditEventService;
    private final OutboxEventService outboxEventService;
    private final RiskDecisionService riskDecisionService;

    public TransferService(
            WalletRepository walletRepository,
            CustomerRepository customerRepository,
            LedgerAccountRepository ledgerAccountRepository,
            TransferRepository transferRepository,
            WalletAvailableBalanceService availableBalanceService,
            LedgerService ledgerService,
            AuditEventService auditEventService,
            OutboxEventService outboxEventService,
            RiskDecisionService riskDecisionService
    ) {
        this.walletRepository = walletRepository;
        this.customerRepository = customerRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.transferRepository = transferRepository;
        this.availableBalanceService = availableBalanceService;
        this.ledgerService = ledgerService;
        this.auditEventService = auditEventService;
        this.outboxEventService = outboxEventService;
        this.riskDecisionService = riskDecisionService;
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
        validateCustomerKyc(sourceWallet, destinationWallet);

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

        WalletAvailableBalance sourceBalance = availableBalanceService.calculate(
                sourceWallet,
                sourceLedgerAccount
        );

        if (sourceBalance.availableBalanceMinor() < command.amountMinor()) {
            throw new InsufficientFundsException(
                    "Source wallet does not have sufficient available funds."
            );
        }

        requireRiskApproval(command);

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

        Transfer transfer = transferRepository.saveAndFlush(Transfer.completed(
                transferId,
                sourceWallet.getId(),
                destinationWallet.getId(),
                command.initiatedByCustomerId(),
                journalEntry.getId(),
                command.amountMinor(),
                command.currency(),
                command.idempotencyKey(),
                fingerprint
        ));

        Map<String, Object> eventData = Map.of(
                "sourceWalletId", sourceWallet.getId().toString(),
                "destinationWalletId", destinationWallet.getId().toString(),
                "amountMinor", command.amountMinor(),
                "currency", command.currency(),
                "journalEntryId", journalEntry.getId().toString()
        );

        auditEventService.record(
                new AuditEventCommand(
                        "INTERNAL_TRANSFER_COMPLETED",
                        command.initiatedByExternalSubject(),
                        command.initiatedByCustomerId(),
                        "TRANSFER",
                        transfer.getId(),
                        eventData
                )
        );

        outboxEventService.enqueue(
                new OutboxEventCommand(
                        "INTERNAL_TRANSFER_COMPLETED",
                        "TRANSFER",
                        transfer.getId(),
                        eventData
                )
        );

        return transfer;
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
            throw new WalletAccessDeniedException(
                    "The authenticated customer cannot initiate a transfer from this wallet."
            );
        }

        if (sourceWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "WALLET_NOT_ACTIVE",
                    "Source wallet is not active."
            );
        }

        if (destinationWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "WALLET_NOT_ACTIVE",
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

    private void validateCustomerKyc(
            Wallet sourceWallet,
            Wallet destinationWallet
    ) {
        Map<UUID, Customer> customersById = customerRepository
                .findByIdIn(Set.of(
                        sourceWallet.getCustomerId(),
                        destinationWallet.getCustomerId()
                ))
                .stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));

        Customer sourceCustomer = customersById.get(sourceWallet.getCustomerId());
        Customer destinationCustomer =
                customersById.get(destinationWallet.getCustomerId());

        if (sourceCustomer == null || destinationCustomer == null) {
            throw new ResourceNotFoundException(
                    "Transfer customer context could not be resolved."
            );
        }

        if (sourceCustomer.getKycStatus() != KycStatus.APPROVED) {
            throw new BusinessRuleViolationException(
                    "KYC_NOT_VERIFIED",
                    "Source customer must have approved KYC before initiating a transfer."
            );
        }

        if (destinationCustomer.getKycStatus() != KycStatus.APPROVED) {
            throw new BusinessRuleViolationException(
                    "KYC_NOT_VERIFIED",
                    "Destination customer must have approved KYC before receiving a transfer."
            );
        }
    }

    private void requireRiskApproval(CreateTransferCommand command) {
        RiskDecision decision = riskDecisionService.assessTransfer(
                new TransferRiskRequest(
                        command.initiatedByCustomerId(),
                        command.sourceWalletId(),
                        command.destinationWalletId(),
                        command.amountMinor(),
                        command.currency()
                ),
                command.initiatedByExternalSubject()
        );

        if (!decision.allowed()) {
            throw new BusinessRuleViolationException(
                    "RISK_DENIED",
                    "Transfer was rejected by risk controls."
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
