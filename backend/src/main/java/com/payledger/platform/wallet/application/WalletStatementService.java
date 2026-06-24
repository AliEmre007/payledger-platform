package com.payledger.platform.wallet.application;

import com.payledger.platform.ledger.application.LedgerAccountService;
import com.payledger.platform.ledger.application.LedgerBalance;
import com.payledger.platform.ledger.application.LedgerBalanceService;
import com.payledger.platform.ledger.application.LedgerStatementPage;
import com.payledger.platform.ledger.application.LedgerStatementService;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.shared.error.WalletAccessDeniedException;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletStatementService {

    private final WalletRepository walletRepository;
    private final LedgerAccountService ledgerAccountService;
    private final LedgerBalanceService ledgerBalanceService;
    private final LedgerStatementService ledgerStatementService;

    public WalletStatementService(
            WalletRepository walletRepository,
            LedgerAccountService ledgerAccountService,
            LedgerBalanceService ledgerBalanceService,
            LedgerStatementService ledgerStatementService
    ) {
        this.walletRepository = walletRepository;
        this.ledgerAccountService = ledgerAccountService;
        this.ledgerBalanceService = ledgerBalanceService;
        this.ledgerStatementService = ledgerStatementService;
    }

    @Transactional(readOnly = true)
    public WalletStatementSnapshot getStatement(
            UUID walletId,
            UUID customerId,
            int page,
            int size
    ) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found: " + walletId
                ));

        requireOwner(wallet, customerId);

        LedgerAccount ledgerAccount = ledgerAccountService.getForWallet(
                wallet.getId()
        );

        LedgerBalance ledgerBalance = ledgerBalanceService.calculate(
                ledgerAccount.getId()
        );

        LedgerStatementPage statementPage = ledgerStatementService
                .getForLedgerAccount(
                        ledgerAccount.getId(),
                        page,
                        size
                );

        List<WalletStatementLine> lines = statementPage.entries()
                .stream()
                .map(entry -> new WalletStatementLine(
                        entry.postingId(),
                        entry.journalEntryId(),
                        entry.journalType(),
                        entry.referenceType(),
                        entry.referenceId(),
                        entry.description(),
                        entry.occurredAt(),
                        entry.direction(),
                        entry.amountMinor(),
                        signedWalletAmount(entry.direction(), entry.amountMinor()),
                        entry.currency(),
                        entry.lineNumber()
                ))
                .toList();

        return new WalletStatementSnapshot(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getStatus(),
                ledgerBalance.balanceMinor(),
                statementPage.page(),
                statementPage.size(),
                statementPage.totalEntries(),
                statementPage.hasNext(),
                lines
        );
    }

    private long signedWalletAmount(
            PostingDirection direction,
            long amountMinor
    ) {
        return direction == PostingDirection.CREDIT
                ? amountMinor
                : -amountMinor;
    }

    private void requireOwner(Wallet wallet, UUID customerId) {
        if (!wallet.getCustomerId().equals(customerId)) {
            throw new WalletAccessDeniedException(
                    "The authenticated customer cannot access this wallet."
            );
        }
    }
}
