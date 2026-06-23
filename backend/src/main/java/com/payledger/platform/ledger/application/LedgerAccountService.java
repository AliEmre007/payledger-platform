package com.payledger.platform.ledger.application;

import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.wallet.domain.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerAccountService {

    private final LedgerAccountRepository ledgerAccountRepository;

    public LedgerAccountService(LedgerAccountRepository ledgerAccountRepository) {
        this.ledgerAccountRepository = ledgerAccountRepository;
    }

    @Transactional
    public LedgerAccount createForWallet(Wallet wallet) {
        return ledgerAccountRepository.findByWalletId(wallet.getId())
                .orElseGet(() -> ledgerAccountRepository.save(
                        LedgerAccount.createForWallet(
                                wallet.getId(),
                                wallet.getCurrency()
                        )
                ));
    }
}
