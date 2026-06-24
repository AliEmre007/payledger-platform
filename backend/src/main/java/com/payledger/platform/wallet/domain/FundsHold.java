package com.payledger.platform.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "funds_holds")
public class FundsHold {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "reference_type", nullable = false, length = 50, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FundsHoldStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FundsHold() {
    }

    private FundsHold(
            UUID id,
            UUID walletId,
            String currency,
            long amountMinor,
            String reason,
            String referenceType,
            UUID referenceId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        this.id = id;
        this.walletId = walletId;
        this.currency = currency;
        this.amountMinor = amountMinor;
        this.reason = reason;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.status = FundsHoldStatus.ACTIVE;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = Instant.now();
    }

    public static FundsHold active(
            UUID walletId,
            String currency,
            long amountMinor,
            String reason,
            String referenceType,
            UUID referenceId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        return new FundsHold(
                UUID.randomUUID(),
                walletId,
                currency,
                amountMinor,
                reason,
                referenceType,
                referenceId,
                idempotencyKey,
                requestFingerprint
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public FundsHoldStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean capture() {
        if (status == FundsHoldStatus.CAPTURED) {
            return false;
        }

        if (status != FundsHoldStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only ACTIVE holds can be captured."
            );
        }

        status = FundsHoldStatus.CAPTURED;
        capturedAt = Instant.now();
        return true;
    }

    public boolean release() {
        if (status == FundsHoldStatus.RELEASED) {
            return false;
        }

        if (status != FundsHoldStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only ACTIVE holds can be released."
            );
        }

        status = FundsHoldStatus.RELEASED;
        releasedAt = Instant.now();
        return true;
    }

    public boolean expire() {
        if (status == FundsHoldStatus.EXPIRED) {
            return false;
        }

        if (status != FundsHoldStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only ACTIVE holds can be expired."
            );
        }

        status = FundsHoldStatus.EXPIRED;
        expiredAt = Instant.now();
        return true;
    }
}
