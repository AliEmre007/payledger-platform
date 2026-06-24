package com.payledger.platform.wallet.application;

import com.payledger.platform.ledger.domain.PostingDirection;

import java.time.Instant;
import java.util.UUID;

public record WalletStatementLine(
        UUID postingId,
        UUID journalEntryId,
        String journalType,
        String referenceType,
        UUID referenceId,
        String description,
        Instant occurredAt,
        PostingDirection direction,
        long amountMinor,
        long signedAmountMinor,
        String currency,
        int lineNumber
) {
}
