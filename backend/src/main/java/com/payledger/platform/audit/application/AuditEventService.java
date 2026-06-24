package com.payledger.platform.audit.application;

import com.payledger.platform.shared.web.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditEventService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditEventService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(AuditEventCommand command) {
        jdbcTemplate.update(
                """
                        INSERT INTO audit_events (
                            id,
                            action_type,
                            actor_external_subject,
                            actor_customer_id,
                            resource_type,
                            resource_id,
                            trace_id,
                            metadata
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """,
                UUID.randomUUID(),
                command.actionType(),
                command.actorExternalSubject(),
                command.actorCustomerId(),
                command.resourceType(),
                command.resourceId(),
                currentTraceId(),
                toJson(command.metadata())
        );
    }

    private UUID currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);

        if (traceId == null || traceId.isBlank()) {
            return null;
        }

        return UUID.fromString(traceId);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(
                    metadata == null ? Map.of() : metadata
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Audit metadata must be JSON serializable.",
                    exception
            );
        }
    }
}
