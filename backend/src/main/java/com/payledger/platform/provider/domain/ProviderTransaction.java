package com.payledger.platform.provider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "provider_transactions")
public class ProviderTransaction {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 50, updatable = false)
    private String providerName;

    @Column(name = "provider_transaction_id", nullable = false, length = 120, updatable = false)
    private String providerTransactionId;

    @Column(name = "payment_intent_id", nullable = false, updatable = false)
    private UUID paymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderTransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_outcome", nullable = false, length = 20, updatable = false)
    private ProviderTransactionStatus requestedOutcome;

    protected ProviderTransaction() {
    }

    private ProviderTransaction(
            UUID id,
            String providerName,
            String providerTransactionId,
            UUID paymentIntentId,
            ProviderTransactionStatus requestedOutcome
    ) {
        this.id = id;
        this.providerName = providerName.trim().toUpperCase(Locale.ROOT);
        this.providerTransactionId = providerTransactionId;
        this.paymentIntentId = paymentIntentId;
        this.status = ProviderTransactionStatus.PENDING;
        this.requestedOutcome = requestedOutcome;
    }

    public static ProviderTransaction pending(
            String providerName,
            String providerTransactionId,
            UUID paymentIntentId,
            ProviderTransactionStatus requestedOutcome
    ) {
        return new ProviderTransaction(
                UUID.randomUUID(),
                providerName,
                providerTransactionId,
                paymentIntentId,
                requestedOutcome
        );
    }

    public void succeed() {
        status = ProviderTransactionStatus.SUCCEEDED;
    }

    public void fail() {
        status = ProviderTransactionStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public UUID getPaymentIntentId() {
        return paymentIntentId;
    }

    public ProviderTransactionStatus getStatus() {
        return status;
    }

    public ProviderTransactionStatus getRequestedOutcome() {
        return requestedOutcome;
    }
}
