package com.payledger.platform.ledger.application;

import com.payledger.platform.ledger.domain.PostingDirection;

import java.time.Instant;
import java.util.UUID;

public record LedgerStatementEntry(
        UUID postingId,
        UUID journalEntryId,
        String journalType,
        String referenceType,
        UUID referenceId,
        String description,
        Instant occurredAt,
        PostingDirection direction,
        long amountMinor,
        String currency,
        int lineNumber
) {
}
