package com.payledger.platform.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_cases")
public class ReconciliationCase {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "settlement_batch_id", nullable = false, updatable = false)
    private UUID settlementBatchId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "provider_reference", nullable = false, length = 120, updatable = false)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "expected_amount_minor", nullable = false, updatable = false)
    private long expectedAmountMinor;

    @Column(name = "actual_amount_minor", nullable = false, updatable = false)
    private long actualAmountMinor;

    @Column(name = "expected_currency", nullable = false, length = 3, updatable = false)
    private String expectedCurrency;

    @Column(name = "actual_currency", nullable = false, length = 3, updatable = false)
    private String actualCurrency;

    @Column(name = "discrepancy_reason", length = 120)
    private String discrepancyReason;

    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    @Column(name = "actor_external_subject", nullable = false, length = 255, updatable = false)
    private String actorExternalSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ReconciliationCase() {
    }

    private ReconciliationCase(
            UUID settlementBatchId,
            UUID merchantId,
            String providerReference,
            ReconciliationStatus status,
            long expectedAmountMinor,
            long actualAmountMinor,
            String expectedCurrency,
            String actualCurrency,
            String discrepancyReason,
            String actorExternalSubject
    ) {
        this.id = UUID.randomUUID();
        this.settlementBatchId = settlementBatchId;
        this.merchantId = merchantId;
        this.providerReference = providerReference.trim();
        this.status = status;
        this.expectedAmountMinor = expectedAmountMinor;
        this.actualAmountMinor = actualAmountMinor;
        this.expectedCurrency = expectedCurrency;
        this.actualCurrency = actualCurrency;
        this.discrepancyReason = discrepancyReason;
        this.actorExternalSubject = actorExternalSubject.trim();
        this.createdAt = Instant.now();
    }

    public static ReconciliationCase create(
            SettlementBatch batch,
            String providerReference,
            long actualAmountMinor,
            String actualCurrency,
            String actorExternalSubject
    ) {
        boolean matched = batch.getTotalAmountMinor() == actualAmountMinor
                && batch.getCurrency().equals(actualCurrency);

        return new ReconciliationCase(
                batch.getId(),
                batch.getMerchantId(),
                providerReference,
                matched ? ReconciliationStatus.MATCHED : ReconciliationStatus.OPEN,
                batch.getTotalAmountMinor(),
                actualAmountMinor,
                batch.getCurrency(),
                actualCurrency,
                matched ? null : discrepancyReason(batch, actualAmountMinor, actualCurrency),
                actorExternalSubject
        );
    }

    private static String discrepancyReason(
            SettlementBatch batch,
            long actualAmountMinor,
            String actualCurrency
    ) {
        if (!batch.getCurrency().equals(actualCurrency)) {
            return "CURRENCY_MISMATCH";
        }

        if (batch.getTotalAmountMinor() != actualAmountMinor) {
            return "AMOUNT_MISMATCH";
        }

        return null;
    }

    public UUID getId() {
        return id;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public long getExpectedAmountMinor() {
        return expectedAmountMinor;
    }

    public long getActualAmountMinor() {
        return actualAmountMinor;
    }

    public String getExpectedCurrency() {
        return expectedCurrency;
    }

    public String getActualCurrency() {
        return actualCurrency;
    }

    public String getDiscrepancyReason() {
        return discrepancyReason;
    }
}
