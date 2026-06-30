package com.payledger.platform.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_lines")
public class SettlementLine {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "settlement_batch_id", nullable = false, updatable = false)
    private UUID settlementBatchId;

    @Column(name = "payment_intent_id", nullable = false, updatable = false)
    private UUID paymentIntentId;

    @Column(name = "capture_journal_entry_id", nullable = false, updatable = false)
    private UUID captureJournalEntryId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SettlementLine() {
    }

    private SettlementLine(
            UUID settlementBatchId,
            UUID paymentIntentId,
            UUID captureJournalEntryId,
            long amountMinor,
            String currency
    ) {
        this.id = UUID.randomUUID();
        this.settlementBatchId = settlementBatchId;
        this.paymentIntentId = paymentIntentId;
        this.captureJournalEntryId = captureJournalEntryId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public static SettlementLine create(
            UUID settlementBatchId,
            UUID paymentIntentId,
            UUID captureJournalEntryId,
            long amountMinor,
            String currency
    ) {
        return new SettlementLine(
                settlementBatchId,
                paymentIntentId,
                captureJournalEntryId,
                amountMinor,
                currency
        );
    }
}
