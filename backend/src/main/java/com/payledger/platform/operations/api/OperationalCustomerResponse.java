package com.payledger.platform.operations.api;

import java.time.Instant;
import java.util.UUID;

public record OperationalCustomerResponse(
        UUID id,
        String customerType,
        String legalName,
        String email,
        String status,
        String kycStatus,
        Instant createdAt
) {
}
