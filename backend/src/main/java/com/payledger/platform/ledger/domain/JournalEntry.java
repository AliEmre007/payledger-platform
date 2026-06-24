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
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, length = 20, updatable = false)
    private JournalEntryKind entryKind;

    @Column(name = "journal_type", nullable = false, length = 50, updatable = false)
    private String journalType;

    @Column(name = "reference_type", nullable = false, length = 50, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "reverses_journal_entry_id", updatable = false)
    private UUID reversesJournalEntryId;

    @Column(length = 500, updatable = false)
    private String description;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JournalEntry() {
    }

    private JournalEntry(
            UUID id,
            String journalType,
            String referenceType,
            UUID referenceId,
            String currency,
            String description,
            Instant effectiveAt
    ) {
        this.id = id;
        this.entryKind = JournalEntryKind.NORMAL;
        this.journalType = journalType;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.currency = currency;
        this.description = description;
        this.effectiveAt = effectiveAt;
        this.createdAt = Instant.now();
    }

    public static JournalEntry normal(
            String journalType,
            String referenceType,
            UUID referenceId,
            String currency,
            String description,
            Instant effectiveAt
    ) {
        return new JournalEntry(
                UUID.randomUUID(),
                journalType,
                referenceType,
                referenceId,
                currency,
                description,
                effectiveAt
        );
    }

    public UUID getId() {
        return id;
    }

    public String getCurrency() {
        return currency;
    }
}
