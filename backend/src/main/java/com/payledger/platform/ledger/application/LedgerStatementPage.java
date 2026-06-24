package com.payledger.platform.ledger.application;

import java.util.List;

public record LedgerStatementPage(
        List<LedgerStatementEntry> entries,
        int page,
        int size,
        long totalEntries,
        boolean hasNext
) {
}
