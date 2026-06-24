package com.payledger.platform.wallet.application;

import java.util.UUID;

public record WalletAvailableBalance(
        UUID walletId,
        String currency,
        long ledgerBalanceMinor,
        long heldAmountMinor,
        long availableBalanceMinor
) {
}
