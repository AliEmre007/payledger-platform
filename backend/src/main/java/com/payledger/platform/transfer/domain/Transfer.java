package com.payledger.platform.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_wallet_id", nullable = false, updatable = false)
    private UUID sourceWalletId;

    @Column(name = "destination_wallet_id", nullable = false, updatable = false)
    private UUID destinationWalletId;

    @Column(name = "initiated_by_customer_id", nullable = false, updatable = false)
    private UUID initiatedByCustomerId;

    @Column(name = "journal_entry_id", nullable = false, updatable = false)
    private UUID journalEntryId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransferStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected Transfer() {
    }

    private Transfer(
            UUID id,
            UUID sourceWalletId,
            UUID destinationWalletId,
            UUID initiatedByCustomerId,
            UUID journalEntryId,
            long amountMinor,
            String currency,
            String idempotencyKey,
            String requestFingerprint
    ) {
        this.id = id;
        this.sourceWalletId = sourceWalletId;
        this.destinationWalletId = destinationWalletId;
        this.initiatedByCustomerId = initiatedByCustomerId;
        this.journalEntryId = journalEntryId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = TransferStatus.COMPLETED;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;

        Instant now = Instant.now();
        this.createdAt = now;
        this.completedAt = now;
    }

    public static Transfer completed(
            UUID id,
            UUID sourceWalletId,
            UUID destinationWalletId,
            UUID initiatedByCustomerId,
            UUID journalEntryId,
            long amountMinor,
            String currency,
            String idempotencyKey,
            String requestFingerprint
    ) {
        return new Transfer(
                id,
                sourceWalletId,
                destinationWalletId,
                initiatedByCustomerId,
                journalEntryId,
                amountMinor,
                currency,
                idempotencyKey,
                requestFingerprint
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceWalletId() {
        return sourceWalletId;
    }

    public UUID getDestinationWalletId() {
        return destinationWalletId;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }
}
