package com.payledger.platform.payment.domain;

import com.payledger.platform.shared.error.BusinessRuleViolationException;
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
@Table(name = "payment_intents")
public class PaymentIntent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "source_wallet_id", nullable = false, updatable = false)
    private UUID sourceWalletId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "funds_hold_id")
    private UUID fundsHoldId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentIntentStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentIntent() {
    }

    private PaymentIntent(
            UUID id,
            UUID customerId,
            UUID sourceWalletId,
            UUID merchantId,
            long amountMinor,
            String currency,
            String idempotencyKey,
            String requestFingerprint
    ) {
        this.id = id;
        this.customerId = customerId;
        this.sourceWalletId = sourceWalletId;
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = PaymentIntentStatus.CREATED;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = Instant.now();
    }

    public static PaymentIntent created(
            UUID id,
            UUID customerId,
            UUID sourceWalletId,
            UUID merchantId,
            long amountMinor,
            String currency,
            String idempotencyKey,
            String requestFingerprint
    ) {
        return new PaymentIntent(
                id,
                customerId,
                sourceWalletId,
                merchantId,
                amountMinor,
                currency,
                idempotencyKey,
                requestFingerprint
        );
    }

    public void authorize(UUID fundsHoldId) {
        if (status != PaymentIntentStatus.CREATED) {
            throw new BusinessRuleViolationException(
                    "PAYMENT_INTENT_INVALID_TRANSITION",
                    "Only CREATED payment intents can be authorized."
            );
        }

        this.fundsHoldId = fundsHoldId;
        this.status = PaymentIntentStatus.AUTHORIZED;
        this.authorizedAt = Instant.now();
    }

    public boolean cancel() {
        if (status == PaymentIntentStatus.CANCELED) {
            return false;
        }

        if (status != PaymentIntentStatus.AUTHORIZED) {
            throw new BusinessRuleViolationException(
                    "PAYMENT_INTENT_INVALID_TRANSITION",
                    "Only AUTHORIZED payment intents can be canceled."
            );
        }

        status = PaymentIntentStatus.CANCELED;
        canceledAt = Instant.now();
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getSourceWalletId() {
        return sourceWalletId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getFundsHoldId() {
        return fundsHoldId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentIntentStatus getStatus() {
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

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }
}
