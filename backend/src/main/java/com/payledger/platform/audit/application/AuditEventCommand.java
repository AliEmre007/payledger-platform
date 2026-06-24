package com.payledger.platform.audit.application;

import java.util.Map;
import java.util.UUID;

public record AuditEventCommand(
        String actionType,
        String actorExternalSubject,
        UUID actorCustomerId,
        String resourceType,
        UUID resourceId,
        Map<String, Object> metadata
) {
}
