package com.payledger.platform.ledger.application;

import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.wallet.domain.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LedgerAccountService {

    private final LedgerAccountRepository ledgerAccountRepository;

    public LedgerAccountService(
            LedgerAccountRepository ledgerAccountRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
    }

    @Transactional
    public LedgerAccount createForWallet(Wallet wallet) {
        return ledgerAccountRepository
                .findByWalletId(wallet.getId())
                .orElseGet(() -> ledgerAccountRepository.saveAndFlush(
                        LedgerAccount.createForWallet(
                                wallet.getId(),
                                wallet.getCurrency()
                        )
                ));
    }

    @Transactional(readOnly = true)
    public LedgerAccount getForWallet(UUID walletId) {
        return ledgerAccountRepository
                .findByWalletId(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger account not found for wallet: " + walletId
                ));
    }
}
