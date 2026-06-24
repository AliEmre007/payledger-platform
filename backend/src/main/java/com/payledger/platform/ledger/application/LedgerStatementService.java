package com.payledger.platform.ledger.application;

import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.ledger.infrastructure.LedgerPostingRepository;
import com.payledger.platform.ledger.infrastructure.LedgerStatementRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerStatementService {

    private static final int MAX_PAGE_SIZE = 100;

    private final LedgerPostingRepository ledgerPostingRepository;

    public LedgerStatementService(
            LedgerPostingRepository ledgerPostingRepository
    ) {
        this.ledgerPostingRepository = ledgerPostingRepository;
    }

    @Transactional(readOnly = true)
    public LedgerStatementPage getForLedgerAccount(
            UUID ledgerAccountId,
            int page,
            int size
    ) {
        validatePagination(page, size);

        long offset = Math.multiplyExact((long) page, size);

        long totalEntries = ledgerPostingRepository
                .countByLedgerAccountId(ledgerAccountId);

        List<LedgerStatementEntry> entries = ledgerPostingRepository
                .findStatementRows(ledgerAccountId, size, offset)
                .stream()
                .map(this::toEntry)
                .toList();

        boolean hasNext = offset + entries.size() < totalEntries;

        return new LedgerStatementPage(
                entries,
                page,
                size,
                totalEntries,
                hasNext
        );
    }

    private LedgerStatementEntry toEntry(LedgerStatementRow row) {
        return new LedgerStatementEntry(
                row.getPostingId(),
                row.getJournalEntryId(),
                row.getJournalType(),
                row.getReferenceType(),
                row.getReferenceId(),
                row.getDescription(),
                row.getOccurredAt(),
                PostingDirection.valueOf(row.getDirection()),
                row.getAmountMinor(),
                row.getCurrency(),
                row.getLineNumber()
        );
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be zero or greater."
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100."
            );
        }
    }
}
