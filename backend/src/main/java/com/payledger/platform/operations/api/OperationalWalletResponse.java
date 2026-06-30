package com.payledger.platform.operations.api;

import java.time.Instant;
import java.util.UUID;

public record OperationalWalletResponse(
        UUID id,
        UUID customerId,
        String currency,
        String status,
        Instant createdAt
) {
}
