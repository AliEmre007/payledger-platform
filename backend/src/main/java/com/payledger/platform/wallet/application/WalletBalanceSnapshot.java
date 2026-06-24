package com.payledger.platform.wallet.application;

import com.payledger.platform.wallet.domain.WalletStatus;

import java.util.UUID;

public record WalletBalanceSnapshot(
        UUID walletId,
        String currency,
        WalletStatus status,
        long ledgerBalanceMinor
) {
}
