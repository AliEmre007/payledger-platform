package com.payledger.platform.outbox.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(OutboxEventCommand command) {
        jdbcTemplate.update(
                """
                        INSERT INTO outbox_events (
                            id,
                            event_id,
                            event_type,
                            aggregate_type,
                            aggregate_id,
                            payload
                        )
                        VALUES (?, ?, ?, ?, ?, ?::jsonb)
                        """,
                UUID.randomUUID(),
                eventId(command),
                command.eventType(),
                command.aggregateType(),
                command.aggregateId(),
                toJson(command.payload())
        );
    }

    private UUID eventId(OutboxEventCommand command) {
        String canonicalEvent = String.join(
                "|",
                command.eventType(),
                command.aggregateType(),
                command.aggregateId().toString()
        );

        return UUID.nameUUIDFromBytes(
                canonicalEvent.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(
                    payload == null ? Map.of() : payload
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Outbox payload must be JSON serializable.",
                    exception
            );
        }
    }
}
