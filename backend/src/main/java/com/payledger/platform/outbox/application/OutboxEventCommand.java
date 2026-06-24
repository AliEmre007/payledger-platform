package com.payledger.platform.outbox.application;

import java.util.Map;
import java.util.UUID;

public record OutboxEventCommand(
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> payload
) {
}
