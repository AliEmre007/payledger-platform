package com.payledger.platform.wallet.application;

import com.payledger.platform.wallet.domain.WalletStatus;

import java.util.List;
import java.util.UUID;

public record WalletStatementSnapshot(
        UUID walletId,
        String currency,
        WalletStatus status,
        long ledgerBalanceMinor,
        int page,
        int size,
        long totalEntries,
        boolean hasNext,
        List<WalletStatementLine> entries
) {
}
