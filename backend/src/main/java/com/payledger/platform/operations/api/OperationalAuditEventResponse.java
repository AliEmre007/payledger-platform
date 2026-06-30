package com.payledger.platform.operations.api;

import java.time.Instant;
import java.util.UUID;

public record OperationalAuditEventResponse(
        UUID id,
        String actionType,
        String actorExternalSubject,
        UUID actorCustomerId,
        String resourceType,
        UUID resourceId,
        String metadata,
        Instant createdAt
) {
}
