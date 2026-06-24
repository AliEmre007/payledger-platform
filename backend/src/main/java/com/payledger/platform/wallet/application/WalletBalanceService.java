package com.payledger.platform.wallet.application;

import com.payledger.platform.ledger.application.LedgerAccountService;
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
    private final WalletAvailableBalanceService availableBalanceService;

    public WalletBalanceService(
            WalletRepository walletRepository,
            LedgerAccountService ledgerAccountService,
            WalletAvailableBalanceService availableBalanceService
    ) {
        this.walletRepository = walletRepository;
        this.ledgerAccountService = ledgerAccountService;
        this.availableBalanceService = availableBalanceService;
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

        WalletAvailableBalance availableBalance =
                availableBalanceService.calculate(wallet, ledgerAccount);

        return new WalletBalanceSnapshot(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getStatus(),
                availableBalance.ledgerBalanceMinor(),
                availableBalance.heldAmountMinor(),
                availableBalance.availableBalanceMinor()
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
