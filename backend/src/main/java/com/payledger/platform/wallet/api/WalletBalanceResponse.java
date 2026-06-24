package com.payledger.platform.wallet.api;

import com.payledger.platform.wallet.application.WalletBalanceSnapshot;
import com.payledger.platform.wallet.domain.WalletStatus;

import java.util.UUID;

public record WalletBalanceResponse(
        UUID walletId,
        String currency,
        WalletStatus status,
        long ledgerBalanceMinor
) {
    public static WalletBalanceResponse from(
            WalletBalanceSnapshot snapshot
    ) {
        return new WalletBalanceResponse(
                snapshot.walletId(),
                snapshot.currency(),
                snapshot.status(),
                snapshot.ledgerBalanceMinor()
        );
    }
}
