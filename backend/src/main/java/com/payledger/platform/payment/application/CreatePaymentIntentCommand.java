package com.payledger.platform.payment.application;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record CreatePaymentIntentCommand(
        UUID customerId,
        String externalSubject,
        UUID sourceWalletId,
        UUID merchantId,
        long amountMinor,
        String currency,
        String idempotencyKey
) {
    public CreatePaymentIntentCommand {
        customerId = Objects.requireNonNull(
                customerId,
                "customerId is required."
        );
        sourceWalletId = Objects.requireNonNull(
                sourceWalletId,
                "sourceWalletId is required."
        );
        merchantId = Objects.requireNonNull(
                merchantId,
                "merchantId is required."
        );

        if (externalSubject != null) {
            externalSubject = externalSubject.trim();
            if (externalSubject.isBlank()) {
                externalSubject = null;
            }
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive.");
        }

        currency = Objects.requireNonNull(currency, "currency is required.")
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(
                    "currency must be a three-letter ISO code."
            );
        }

        idempotencyKey = Objects.requireNonNull(
                idempotencyKey,
                "idempotencyKey is required."
        ).trim();

        if (idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new IllegalArgumentException(
                    "idempotencyKey must contain 1 to 255 characters."
            );
        }
    }
}
