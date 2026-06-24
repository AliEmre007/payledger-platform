package com.payledger.platform.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_postings")
public class LedgerPosting {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "journal_entry_id", nullable = false, updatable = false)
    private UUID journalEntryId;

    @Column(name = "ledger_account_id", nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Column(name = "line_number", nullable = false, updatable = false)
    private short lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private PostingDirection direction;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerPosting() {
    }

    private LedgerPosting(
            UUID journalEntryId,
            UUID ledgerAccountId,
            short lineNumber,
            PostingDirection direction,
            long amountMinor,
            String currency
    ) {
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("Ledger posting line number must be positive.");
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Ledger posting amount must be positive.");
        }

        this.id = UUID.randomUUID();
        this.journalEntryId = journalEntryId;
        this.ledgerAccountId = ledgerAccountId;
        this.lineNumber = lineNumber;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public static LedgerPosting create(
            UUID journalEntryId,
            UUID ledgerAccountId,
            short lineNumber,
            PostingDirection direction,
            long amountMinor,
            String currency
    ) {
        return new LedgerPosting(
                journalEntryId,
                ledgerAccountId,
                lineNumber,
                direction,
                amountMinor,
                currency
        );
    }
}
