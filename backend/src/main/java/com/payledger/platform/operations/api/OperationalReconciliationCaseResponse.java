package com.payledger.platform.operations.api;

import java.time.Instant;
import java.util.UUID;

public record OperationalReconciliationCaseResponse(
        UUID id,
        UUID settlementBatchId,
        UUID merchantId,
        String providerReference,
        String status,
        long expectedAmountMinor,
        long actualAmountMinor,
        String expectedCurrency,
        String actualCurrency,
        String discrepancyReason,
        Instant createdAt
) {
}
