package com.payledger.platform.ledger.infrastructure;

import java.time.Instant;
import java.util.UUID;

public interface LedgerStatementRow {

    UUID getPostingId();

    UUID getJournalEntryId();

    String getJournalType();

    String getReferenceType();

    UUID getReferenceId();

    String getDescription();

    Instant getOccurredAt();

    String getDirection();

    long getAmountMinor();

    String getCurrency();

    int getLineNumber();
}
