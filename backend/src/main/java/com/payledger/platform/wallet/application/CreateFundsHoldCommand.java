package com.payledger.platform.wallet.application;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record CreateFundsHoldCommand(
        UUID walletId,
        long amountMinor,
        String currency,
        String reason,
        String referenceType,
        UUID referenceId,
        String idempotencyKey
) {
    public CreateFundsHoldCommand {
        walletId = Objects.requireNonNull(walletId, "walletId is required.");
        referenceId = Objects.requireNonNull(
                referenceId,
                "referenceId is required."
        );
        currency = normalizeRequired(currency, "currency");
        reason = normalizeRequired(reason, "reason");
        referenceType = normalizeRequired(referenceType, "referenceType");
        idempotencyKey = normalizeRequired(idempotencyKey, "idempotencyKey");

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive.");
        }

        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "currency must be an uppercase ISO-style code."
            );
        }

        if (reason.length() > 500) {
            throw new IllegalArgumentException(
                    "reason must not exceed 500 characters."
            );
        }

        if (referenceType.length() > 50) {
            throw new IllegalArgumentException(
                    "referenceType must not exceed 50 characters."
            );
        }

        if (idempotencyKey.length() > 255) {
            throw new IllegalArgumentException(
                    "idempotencyKey must not exceed 255 characters."
            );
        }
    }

    private static String normalizeRequired(String value, String name) {
        String normalized = Objects.requireNonNull(
                value,
                name + " is required."
        ).trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }

        if ("currency".equals(name)) {
            return normalized.toUpperCase(Locale.ROOT);
        }

        if ("referenceType".equals(name)) {
            return normalized.toUpperCase(Locale.ROOT);
        }

        return normalized;
    }
}
