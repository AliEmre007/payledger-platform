package com.payledger.platform.notification.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationOutboxProcessor {

    private static final int MAX_ATTEMPTS = 3;
    private static final String CHANNEL = "LOCAL_LOG";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxNotificationMapper notificationMapper;
    private final NotificationDeliveryAdapter deliveryAdapter;

    public NotificationOutboxProcessor(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            OutboxNotificationMapper notificationMapper,
            NotificationDeliveryAdapter deliveryAdapter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.notificationMapper = notificationMapper;
        this.deliveryAdapter = deliveryAdapter;
    }

    @Transactional
    public int processPending(int limit) {
        int processed = 0;
        int batchSize = Math.max(1, limit);

        while (processed < batchSize && processNext()) {
            processed++;
        }

        return processed;
    }

    @Transactional
    public boolean processNext() {
        Optional<OutboxRow> outboxRow = lockNextOutboxEvent();

        if (outboxRow.isEmpty()) {
            return false;
        }

        process(outboxRow.get());
        return true;
    }

    private void process(OutboxRow outboxRow) {
        Map<String, Object> payload = readPayload(outboxRow.payload());
        Optional<NotificationMessage> message = notificationMapper.map(
                outboxRow.eventType(),
                outboxRow.aggregateType(),
                outboxRow.aggregateId().toString(),
                payload
        );

        if (message.isEmpty()) {
            markOutboxProcessed(outboxRow.id());
            return;
        }

        NotificationRow notification = findOrCreateNotification(
                outboxRow,
                message.get()
        );

        if ("SENT".equals(notification.status())
                || "FAILED".equals(notification.status())) {
            markOutboxTerminal(outboxRow.id(), notification.status(), null);
            return;
        }

        int nextAttempt = notification.attemptCount() + 1;

        try {
            deliveryAdapter.deliver(
                    new NotificationDeliveryCommand(
                            notification.id(),
                            CHANNEL,
                            notification.recipientType(),
                            notification.recipientReference(),
                            notification.subject(),
                            notification.body(),
                            message.get().forceFailure()
                    )
            );
            recordAttempt(notification.id(), nextAttempt, "SUCCEEDED", null);
            markNotificationSent(notification.id(), nextAttempt);
            markOutboxProcessed(outboxRow.id());
        } catch (RuntimeException exception) {
            String error = truncate(exception.getMessage(), 500);
            recordAttempt(notification.id(), nextAttempt, "FAILED", error);

            if (nextAttempt >= MAX_ATTEMPTS) {
                markNotificationFailed(notification.id(), nextAttempt, error);
                markOutboxFailed(outboxRow.id(), nextAttempt, error);
            } else {
                markNotificationPending(notification.id(), nextAttempt, error);
                markOutboxRetryable(outboxRow.id(), nextAttempt, error);
            }
        }
    }

    private Optional<OutboxRow> lockNextOutboxEvent() {
        return jdbcTemplate.query(
                """
                        SELECT id, event_id, event_type, aggregate_type,
                               aggregate_id, payload::text AS payload
                        FROM outbox_events
                        WHERE status = 'PENDING'
                        ORDER BY created_at ASC, id ASC
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(mapOutboxRow(resultSet))
                        : Optional.empty()
        );
    }

    private NotificationRow findOrCreateNotification(
            OutboxRow outboxRow,
            NotificationMessage message
    ) {
        Optional<NotificationRow> existing = jdbcTemplate.query(
                """
                        SELECT id, recipient_type, recipient_reference,
                               subject, body, status, attempt_count
                        FROM notification_records
                        WHERE outbox_event_id = ?
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(mapNotificationRow(resultSet))
                        : Optional.empty(),
                outboxRow.id()
        );

        if (existing.isPresent()) {
            return existing.get();
        }

        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO notification_records (
                            id,
                            outbox_event_id,
                            event_id,
                            event_type,
                            aggregate_type,
                            aggregate_id,
                            recipient_type,
                            recipient_reference,
                            channel,
                            subject,
                            body,
                            status
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                        """,
                notificationId,
                outboxRow.id(),
                outboxRow.eventId(),
                outboxRow.eventType(),
                outboxRow.aggregateType(),
                outboxRow.aggregateId(),
                message.recipientType(),
                truncate(message.recipientReference(), 255),
                CHANNEL,
                truncate(message.subject(), 120),
                truncate(message.body(), 1000)
        );

        return new NotificationRow(
                notificationId,
                message.recipientType(),
                truncate(message.recipientReference(), 255),
                truncate(message.subject(), 120),
                truncate(message.body(), 1000),
                "PENDING",
                0
        );
    }

    private void recordAttempt(
            UUID notificationId,
            int attemptNumber,
            String status,
            String error
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO notification_delivery_attempts (
                            id,
                            notification_id,
                            attempt_number,
                            status,
                            error_message
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                notificationId,
                attemptNumber,
                status,
                error
        );
    }

    private void markNotificationSent(UUID notificationId, int attemptCount) {
        jdbcTemplate.update(
                """
                        UPDATE notification_records
                        SET status = 'SENT',
                            attempt_count = ?,
                            last_error = NULL,
                            sent_at = now()
                        WHERE id = ?
                        """,
                attemptCount,
                notificationId
        );
    }

    private void markNotificationPending(
            UUID notificationId,
            int attemptCount,
            String error
    ) {
        jdbcTemplate.update(
                """
                        UPDATE notification_records
                        SET status = 'PENDING',
                            attempt_count = ?,
                            last_error = ?
                        WHERE id = ?
                        """,
                attemptCount,
                error,
                notificationId
        );
    }

    private void markNotificationFailed(
            UUID notificationId,
            int attemptCount,
            String error
    ) {
        jdbcTemplate.update(
                """
                        UPDATE notification_records
                        SET status = 'FAILED',
                            attempt_count = ?,
                            last_error = ?
                        WHERE id = ?
                        """,
                attemptCount,
                error,
                notificationId
        );
    }

    private void markOutboxProcessed(UUID outboxId) {
        markOutboxTerminal(outboxId, "PROCESSED", null);
    }

    private void markOutboxFailed(
            UUID outboxId,
            int attemptCount,
            String error
    ) {
        jdbcTemplate.update(
                """
                        UPDATE outbox_events
                        SET status = 'FAILED',
                            attempt_count = ?,
                            processed_at = NULL,
                            last_error = ?
                        WHERE id = ?
                        """,
                attemptCount,
                error,
                outboxId
        );
    }

    private void markOutboxRetryable(
            UUID outboxId,
            int attemptCount,
            String error
    ) {
        jdbcTemplate.update(
                """
                        UPDATE outbox_events
                        SET status = 'PENDING',
                            attempt_count = ?,
                            processed_at = NULL,
                            last_error = ?
                        WHERE id = ?
                        """,
                attemptCount,
                error,
                outboxId
        );
    }

    private void markOutboxTerminal(
            UUID outboxId,
            String notificationStatus,
            String error
    ) {
        String outboxStatus = "FAILED".equals(notificationStatus)
                ? "FAILED"
                : "PROCESSED";
        jdbcTemplate.update(
                """
                        UPDATE outbox_events
                        SET status = ?,
                            processed_at = now(),
                            last_error = ?
                        WHERE id = ?
                        """,
                outboxStatus,
                error,
                outboxId
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Outbox payload must be valid JSON.",
                    exception
            );
        }
    }

    private OutboxRow mapOutboxRow(ResultSet resultSet) throws SQLException {
        return new OutboxRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getString("aggregate_type"),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getString("payload")
        );
    }

    private NotificationRow mapNotificationRow(ResultSet resultSet)
            throws SQLException {
        return new NotificationRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("recipient_type"),
                resultSet.getString("recipient_reference"),
                resultSet.getString("subject"),
                resultSet.getString("body"),
                resultSet.getString("status"),
                resultSet.getInt("attempt_count")
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private record OutboxRow(
            UUID id,
            UUID eventId,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String payload
    ) {
    }

    private record NotificationRow(
            UUID id,
            String recipientType,
            String recipientReference,
            String subject,
            String body,
            String status,
            int attemptCount
    ) {
    }
}
