package com.payledger.platform.operations.api;

import java.time.Instant;
import java.util.UUID;

public record OperationalPaymentIntentResponse(
        UUID id,
        UUID customerId,
        UUID sourceWalletId,
        UUID merchantId,
        long amountMinor,
        String currency,
        String status,
        Instant createdAt,
        Instant authorizedAt,
        Instant capturedAt,
        Instant refundedAt
) {
}
