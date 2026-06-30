package com.payledger.platform.ledger.application;

import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.ledger.domain.LedgerAccountType;
import com.payledger.platform.ledger.infrastructure.LedgerAccountRepository;
import com.payledger.platform.shared.error.ResourceNotFoundException;
import com.payledger.platform.wallet.domain.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
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

    @Transactional
    public LedgerAccount createForMerchantPayable(
            UUID merchantId,
            String currency
    ) {
        return ledgerAccountRepository
                .findByMerchantIdAndCurrency(merchantId, currency)
                .orElseGet(() -> ledgerAccountRepository.saveAndFlush(
                        LedgerAccount.createForMerchantPayable(
                                merchantId,
                                currency
                        )
                ));
    }

    @Transactional(readOnly = true)
    public LedgerAccount getForMerchantPayable(
            UUID merchantId,
            String currency
    ) {
        return ledgerAccountRepository
                .findByMerchantIdAndCurrency(merchantId, currency)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger account not found for merchant payable: "
                                + merchantId
                ));
    }

    @Transactional
    public LedgerAccount getOrCreateSettlementClearing(String currency) {
        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        String accountCode = "SETTLEMENT_CLEARING_" + normalizedCurrency;

        return ledgerAccountRepository
                .findByAccountCode(accountCode)
                .orElseGet(() -> ledgerAccountRepository.saveAndFlush(
                        LedgerAccount.createPlatformAccount(
                                accountCode,
                                LedgerAccountType.ASSET,
                                normalizedCurrency
                        )
                ));
    }
}
