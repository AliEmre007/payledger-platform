package com.payledger.platform.wallet.application;

import com.payledger.platform.ledger.application.LedgerBalance;
import com.payledger.platform.ledger.application.LedgerBalanceService;
import com.payledger.platform.ledger.domain.LedgerAccount;
import com.payledger.platform.wallet.domain.FundsHoldStatus;
import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.infrastructure.FundsHoldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletAvailableBalanceService {

    private final LedgerBalanceService ledgerBalanceService;
    private final FundsHoldRepository fundsHoldRepository;

    public WalletAvailableBalanceService(
            LedgerBalanceService ledgerBalanceService,
            FundsHoldRepository fundsHoldRepository
    ) {
        this.ledgerBalanceService = ledgerBalanceService;
        this.fundsHoldRepository = fundsHoldRepository;
    }

    @Transactional(readOnly = true)
    public WalletAvailableBalance calculate(
            Wallet wallet,
            LedgerAccount ledgerAccount
    ) {
        LedgerBalance ledgerBalance = ledgerBalanceService.calculate(
                ledgerAccount.getId()
        );
        long heldAmountMinor =
                fundsHoldRepository.sumAmountMinorByWalletIdAndStatus(
                        wallet.getId(),
                        FundsHoldStatus.ACTIVE
                );

        return new WalletAvailableBalance(
                wallet.getId(),
                wallet.getCurrency(),
                ledgerBalance.balanceMinor(),
                heldAmountMinor,
                Math.subtractExact(
                        ledgerBalance.balanceMinor(),
                        heldAmountMinor
                )
        );
    }
}
