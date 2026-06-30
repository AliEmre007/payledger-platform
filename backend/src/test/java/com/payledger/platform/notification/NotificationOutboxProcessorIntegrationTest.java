package com.payledger.platform.notification;

import com.payledger.platform.notification.application.NotificationOutboxProcessor;
import com.payledger.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationOutboxProcessorIntegrationTest
        extends PostgresIntegrationTest {

    @Autowired
    private NotificationOutboxProcessor processor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void markExistingPendingOutboxProcessed() {
        jdbcTemplate.update(
                """
                        UPDATE outbox_events
                        SET status = 'PROCESSED',
                            processed_at = now(),
                            last_error = NULL
                        WHERE status = 'PENDING'
                        """
        );
    }

    @Test
    void outboxEventIsProcessedOnce() {
        UUID outboxId = insertOutboxEvent(
                "PAYMENT_INTENT_AUTHORIZED",
                "PAYMENT_INTENT",
                Map.of(
                        "customerId", UUID.randomUUID().toString(),
                        "amountMinor", 2_500,
                        "currency", "TRY"
                )
        );

        assertThat(processor.processNext()).isTrue();
        assertThat(processor.processNext()).isFalse();

        assertThat(countNotifications(outboxId)).isOne();
        assertThat(countAttempts(outboxId)).isOne();
        assertThat(outboxStatus(outboxId)).isEqualTo("PROCESSED");
        assertThat(notificationStatus(outboxId)).isEqualTo("SENT");
    }

    @Test
    void failureRetriesWithoutCreatingDuplicateNotificationOrFinancialEvents() {
        long journalCountBefore = countJournalEntries();
        UUID outboxId = insertOutboxEvent(
                "INTERNAL_TRANSFER_COMPLETED",
                "TRANSFER",
                Map.of(
                        "customerId", UUID.randomUUID().toString(),
                        "sourceWalletId", UUID.randomUUID().toString(),
                        "destinationWalletId", UUID.randomUUID().toString(),
                        "amountMinor", 1_000,
                        "currency", "TRY",
                        "forceDeliveryFailure", true
                )
        );

        assertThat(processor.processNext()).isTrue();
        assertThat(countNotifications(outboxId)).isOne();
        assertThat(countAttempts(outboxId)).isOne();
        assertThat(outboxStatus(outboxId)).isEqualTo("PENDING");
        assertThat(notificationStatus(outboxId)).isEqualTo("PENDING");

        assertThat(processor.processNext()).isTrue();
        assertThat(countNotifications(outboxId)).isOne();
        assertThat(countAttempts(outboxId)).isEqualTo(2);
        assertThat(outboxStatus(outboxId)).isEqualTo("PENDING");

        assertThat(processor.processNext()).isTrue();
        assertThat(countNotifications(outboxId)).isOne();
        assertThat(countAttempts(outboxId)).isEqualTo(3);
        assertThat(outboxStatus(outboxId)).isEqualTo("FAILED");
        assertThat(notificationStatus(outboxId)).isEqualTo("FAILED");
        assertThat(countJournalEntries()).isEqualTo(journalCountBefore);
    }

    @Test
    void notificationContentExcludesSecretsAndUnnecessaryPii() {
        UUID outboxId = insertOutboxEvent(
                "KYC_APPROVED",
                "CUSTOMER",
                Map.of(
                        "customerId", UUID.randomUUID().toString(),
                        "bearerToken", "secret-token-value",
                        "password", "secret-password-value",
                        "email", "sensitive@example.test"
                )
        );

        assertThat(processor.processNext()).isTrue();

        String body = jdbcTemplate.queryForObject(
                """
                        SELECT body
                        FROM notification_records
                        WHERE outbox_event_id = ?
                        """,
                String.class,
                outboxId
        );

        assertThat(body)
                .doesNotContain("secret-token-value")
                .doesNotContain("secret-password-value")
                .doesNotContain("sensitive@example.test");
    }

    private UUID insertOutboxEvent(
            String eventType,
            String aggregateType,
            Map<String, Object> payload
    ) {
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

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
                outboxId,
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                toJson(payload)
        );

        return outboxId;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private long countNotifications(UUID outboxId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM notification_records
                        WHERE outbox_event_id = ?
                        """,
                Long.class,
                outboxId
        );
    }

    private long countAttempts(UUID outboxId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM notification_delivery_attempts attempt
                        JOIN notification_records notification
                          ON notification.id = attempt.notification_id
                        WHERE notification.outbox_event_id = ?
                        """,
                Long.class,
                outboxId
        );
    }

    private String outboxStatus(UUID outboxId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT status
                        FROM outbox_events
                        WHERE id = ?
                        """,
                String.class,
                outboxId
        );
    }

    private String notificationStatus(UUID outboxId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT status
                        FROM notification_records
                        WHERE outbox_event_id = ?
                        """,
                String.class,
                outboxId
        );
    }

    private long countJournalEntries() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM journal_entries",
                Long.class
        );
    }
}
