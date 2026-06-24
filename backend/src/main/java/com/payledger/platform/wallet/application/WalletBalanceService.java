package com.payledger.platform.wallet.application;

import com.payledger.platform.ledger.application.LedgerAccountService;
import com.payledger.platform.ledger.application.LedgerBalance;
import com.payledger.platform.ledger.application.LedgerBalanceService;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.shared.error.WalletAccessDeniedException;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletBalanceService {

    private final WalletRepository walletRepository;
    private final LedgerAccountService ledgerAccountService;
    private final LedgerBalanceService ledgerBalanceService;

    public WalletBalanceService(
            WalletRepository walletRepository,
            LedgerAccountService ledgerAccountService,
            LedgerBalanceService ledgerBalanceService
    ) {
        this.walletRepository = walletRepository;
        this.ledgerAccountService = ledgerAccountService;
        this.ledgerBalanceService = ledgerBalanceService;
    }

    @Transactional(readOnly = true)
    public WalletBalanceSnapshot getBalance(UUID walletId, UUID customerId) {
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

        return new WalletBalanceSnapshot(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getStatus(),
                ledgerBalance.balanceMinor()
        );
    }

    private void requireOwner(Wallet wallet, UUID customerId) {
        if (!wallet.getCustomerId().equals(customerId)) {
            throw new WalletAccessDeniedException(
                    "The authenticated customer cannot access this wallet."
            );
        }
    }
}
