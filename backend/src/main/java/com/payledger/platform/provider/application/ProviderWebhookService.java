package com.payledger.platform.provider.application;

import com.payledger.platform.payment.application.PaymentIntentService;
import com.payledger.platform.provider.domain.ProviderTransaction;
import com.payledger.platform.provider.domain.ProviderTransactionStatus;
import com.payledger.platform.provider.infrastructure.ProviderTransactionRepository;
import com.payledger.platform.shared.error.InvalidWebhookSignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProviderWebhookService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PROVIDER_NAME =
            ProviderSimulatorService.PROVIDER_NAME;

    private final ProviderTransactionRepository transactionRepository;
    private final PaymentIntentService paymentIntentService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public ProviderWebhookService(
            ProviderTransactionRepository transactionRepository,
            PaymentIntentService paymentIntentService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${payledger.provider.webhook-secret:payledger-dev-webhook-secret}")
            String webhookSecret
    ) {
        this.transactionRepository = transactionRepository;
        this.paymentIntentService = paymentIntentService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public ProviderWebhookResult receive(
            String payload,
            String signature
    ) {
        validateSignature(payload, signature);

        ProviderWebhookRequest request = parse(payload);
        if (!"PAYMENT_SUCCEEDED".equals(request.eventType())
                && !"PAYMENT_FAILED".equals(request.eventType())) {
            throw new IllegalArgumentException(
                    "Unsupported provider webhook event type."
            );
        }

        Optional<ProviderWebhookResult> duplicate = findExisting(
                request.eventId()
        );

        if (duplicate.isPresent()) {
            return duplicate.get();
        }

        Optional<ProviderTransaction> transaction =
                transactionRepository.findByProviderTransactionForUpdate(
                        PROVIDER_NAME,
                        request.providerTransactionId()
                );

        if (transaction.isEmpty()) {
            return insertWebhookEvent(
                    request,
                    payload,
                    signature,
                    "IGNORED",
                    "UNKNOWN_PROVIDER_TRANSACTION",
                    null
            );
        }

        ProviderTransaction providerTransaction = transaction.get();

        if (!providerTransaction.getPaymentIntentId()
                .equals(request.paymentIntentId())) {
            return insertWebhookEvent(
                    request,
                    payload,
                    signature,
                    "IGNORED",
                    "PAYMENT_INTENT_MISMATCH",
                    providerTransaction.getPaymentIntentId()
            );
        }

        if ("PAYMENT_FAILED".equals(request.eventType())) {
            providerTransaction.fail();
            transactionRepository.saveAndFlush(providerTransaction);
            return insertWebhookEvent(
                    request,
                    payload,
                    signature,
                    "PROCESSED",
                    null,
                    providerTransaction.getPaymentIntentId()
            );
        }

        if (providerTransaction.getStatus()
                == ProviderTransactionStatus.SUCCEEDED) {
            return insertWebhookEvent(
                    request,
                    payload,
                    signature,
                    "IGNORED",
                    "ALREADY_SUCCEEDED",
                    providerTransaction.getPaymentIntentId()
            );
        }

        if (providerTransaction.getStatus()
                == ProviderTransactionStatus.FAILED) {
            return insertWebhookEvent(
                    request,
                    payload,
                    signature,
                    "IGNORED",
                    "PROVIDER_TRANSACTION_FAILED",
                    providerTransaction.getPaymentIntentId()
            );
        }

        providerTransaction.succeed();
        transactionRepository.saveAndFlush(providerTransaction);
        paymentIntentService.capture(
                providerTransaction.getPaymentIntentId(),
                "provider-capture-" + request.eventId(),
                "fake-provider-webhook"
        );

        return insertWebhookEvent(
                request,
                payload,
                signature,
                "PROCESSED",
                null,
                providerTransaction.getPaymentIntentId()
        );
    }

    public String signatureFor(String payload) {
        return hmacSha256(payload);
    }

    private void validateSignature(
            String payload,
            String signature
    ) {
        if (signature == null || signature.isBlank()) {
            throw new InvalidWebhookSignatureException(
                    "Webhook signature is missing."
            );
        }

        String expected = hmacSha256(payload);
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            throw new InvalidWebhookSignatureException(
                    "Webhook signature is invalid."
            );
        }
    }

    private String hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            ));
            return HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate webhook signature.",
                    exception
            );
        }
    }

    private ProviderWebhookRequest parse(String payload) {
        try {
            ProviderWebhookRequest request = objectMapper.readValue(
                    payload,
                    ProviderWebhookRequest.class
            );

            if (request.eventId() == null
                    || request.eventId().isBlank()
                    || request.eventType() == null
                    || request.eventType().isBlank()
                    || request.providerTransactionId() == null
                    || request.providerTransactionId().isBlank()
                    || request.paymentIntentId() == null) {
                throw new IllegalArgumentException(
                        "Webhook payload is missing required fields."
                );
            }

            return new ProviderWebhookRequest(
                    request.eventId().trim(),
                    request.eventType().trim().toUpperCase(),
                    request.providerTransactionId().trim(),
                    request.paymentIntentId()
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Webhook payload must be valid provider JSON.",
                    exception
            );
        }
    }

    private Optional<ProviderWebhookResult> findExisting(String eventId) {
        return jdbcTemplate.query(
                """
                        SELECT provider_event_id, status, ignored_reason
                        FROM provider_webhook_events
                        WHERE provider_name = ?
                          AND provider_event_id = ?
                        """,
                (resultSet, rowNumber) -> new ProviderWebhookResult(
                        resultSet.getString("provider_event_id"),
                        resultSet.getString("status"),
                        resultSet.getString("ignored_reason")
                ),
                PROVIDER_NAME,
                eventId
        ).stream().findFirst();
    }

    private ProviderWebhookResult insertWebhookEvent(
            ProviderWebhookRequest request,
            String payload,
            String signature,
            String status,
            String ignoredReason,
            UUID storedPaymentIntentId
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO provider_webhook_events (
                            id,
                            provider_name,
                            provider_event_id,
                            provider_transaction_id,
                            payment_intent_id,
                            event_type,
                            payload_sha256,
                            signature_sha256,
                            status,
                            ignored_reason,
                            metadata,
                            processed_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """,
                UUID.randomUUID(),
                PROVIDER_NAME,
                request.eventId(),
                request.providerTransactionId(),
                storedPaymentIntentId,
                request.eventType(),
                sha256(payload),
                sha256(signature),
                status,
                ignoredReason,
                metadata(status, ignoredReason),
                processedAt(status)
        );

        return new ProviderWebhookResult(
                request.eventId(),
                status,
                ignoredReason
        );
    }

    private String metadata(
            String status,
            String ignoredReason
    ) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "provider", PROVIDER_NAME,
                    "status", status,
                    "ignoredReason", ignoredReason == null ? "" : ignoredReason
            ));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Webhook metadata must be JSON serializable.",
                    exception
            );
        }
    }

    private Timestamp processedAt(String status) {
        if (!"PROCESSED".equals(status)) {
            return null;
        }

        return Timestamp.from(Instant.now());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate SHA-256.",
                    exception
            );
        }
    }
}
