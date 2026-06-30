package com.payledger.platform.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "settlement_batches")
public class SettlementBatch {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private SettlementBatchStatus status;

    @Column(name = "total_amount_minor", nullable = false, updatable = false)
    private long totalAmountMinor;

    @Column(name = "journal_entry_id", nullable = false, updatable = false)
    private UUID journalEntryId;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "actor_external_subject", nullable = false, length = 255, updatable = false)
    private String actorExternalSubject;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected SettlementBatch() {
    }

    private SettlementBatch(
            UUID id,
            UUID merchantId,
            String currency,
            long totalAmountMinor,
            UUID journalEntryId,
            String idempotencyKey,
            String actorExternalSubject,
            String reason
    ) {
        this.id = id;
        this.merchantId = merchantId;
        this.currency = normalizeCurrency(currency);
        this.status = SettlementBatchStatus.COMPLETED;
        this.totalAmountMinor = totalAmountMinor;
        this.journalEntryId = journalEntryId;
        this.idempotencyKey = normalizeRequired(idempotencyKey, "idempotencyKey");
        this.actorExternalSubject = normalizeRequired(
                actorExternalSubject,
                "actorExternalSubject"
        );
        this.reason = normalizeRequired(reason, "reason");
        this.createdAt = Instant.now();
        this.completedAt = this.createdAt;
    }

    public static SettlementBatch completed(
            UUID id,
            UUID merchantId,
            String currency,
            long totalAmountMinor,
            UUID journalEntryId,
            String idempotencyKey,
            String actorExternalSubject,
            String reason
    ) {
        if (totalAmountMinor <= 0) {
            throw new IllegalArgumentException("totalAmountMinor must be positive.");
        }

        return new SettlementBatch(
                id,
                merchantId,
                currency,
                totalAmountMinor,
                journalEntryId,
                idempotencyKey,
                actorExternalSubject,
                reason
        );
    }

    private static String normalizeCurrency(String currency) {
        String normalized = normalizeRequired(currency, "currency")
                .toUpperCase(Locale.ROOT);

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(
                    "currency must be a three-letter ISO currency code."
            );
        }

        return normalized;
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getCurrency() {
        return currency;
    }

    public SettlementBatchStatus getStatus() {
        return status;
    }

    public long getTotalAmountMinor() {
        return totalAmountMinor;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
