package com.payledger.platform.ledger.application;

import java.util.UUID;

public record LedgerBalance(
        UUID ledgerAccountId,
        String currency,
        long debitTotalMinor,
        long creditTotalMinor,
        long balanceMinor
) {
}
