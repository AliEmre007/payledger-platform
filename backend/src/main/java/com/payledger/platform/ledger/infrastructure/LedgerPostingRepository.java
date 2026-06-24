package com.payledger.platform.ledger.infrastructure;

import com.payledger.platform.ledger.domain.LedgerPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LedgerPostingRepository
        extends JpaRepository<LedgerPosting, UUID> {

    List<LedgerPosting> findAllByLedgerAccountIdOrderByCreatedAtAscLineNumberAsc(
            UUID ledgerAccountId
    );

    long countByLedgerAccountId(UUID ledgerAccountId);

    @Query(value = """
            SELECT
                p.id AS "postingId",
                p.journal_entry_id AS "journalEntryId",
                j.journal_type AS "journalType",
                j.reference_type AS "referenceType",
                j.reference_id AS "referenceId",
                j.description AS "description",
                j.created_at AS "occurredAt",
                p.direction AS "direction",
                p.amount_minor AS "amountMinor",
                p.currency AS "currency",
                p.line_number AS "lineNumber"
            FROM ledger_postings p
            JOIN journal_entries j
                ON j.id = p.journal_entry_id
            WHERE p.ledger_account_id = :ledgerAccountId
            ORDER BY
                j.created_at DESC,
                p.created_at DESC,
                p.line_number DESC,
                p.id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<LedgerStatementRow> findStatementRows(
            @Param("ledgerAccountId") UUID ledgerAccountId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
