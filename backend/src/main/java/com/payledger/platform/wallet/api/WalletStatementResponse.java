package com.payledger.platform.wallet.api;

import com.payledger.platform.wallet.application.WalletStatementSnapshot;
import com.payledger.platform.wallet.domain.WalletStatus;

import java.util.List;
import java.util.UUID;

public record WalletStatementResponse(
        UUID walletId,
        String currency,
        WalletStatus status,
        long ledgerBalanceMinor,
        int page,
        int size,
        long totalEntries,
        boolean hasNext,
        List<WalletStatementLineResponse> entries
) {
    public static WalletStatementResponse from(
            WalletStatementSnapshot snapshot
    ) {
        return new WalletStatementResponse(
                snapshot.walletId(),
                snapshot.currency(),
                snapshot.status(),
                snapshot.ledgerBalanceMinor(),
                snapshot.page(),
                snapshot.size(),
                snapshot.totalEntries(),
                snapshot.hasNext(),
                snapshot.entries()
                        .stream()
                        .map(WalletStatementLineResponse::from)
                        .toList()
        );
    }
}
