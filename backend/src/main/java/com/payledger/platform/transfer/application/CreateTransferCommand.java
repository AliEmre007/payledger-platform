package com.payledger.platform.transfer.application;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record CreateTransferCommand(
        UUID sourceWalletId,
        UUID destinationWalletId,
        UUID initiatedByCustomerId,
        String initiatedByExternalSubject,
        long amountMinor,
        String currency,
        String idempotencyKey
) {
    public CreateTransferCommand {
        sourceWalletId = Objects.requireNonNull(
                sourceWalletId,
                "sourceWalletId is required."
        );

        destinationWalletId = Objects.requireNonNull(
                destinationWalletId,
                "destinationWalletId is required."
        );

        initiatedByCustomerId = Objects.requireNonNull(
                initiatedByCustomerId,
                "initiatedByCustomerId is required."
        );

        if (initiatedByExternalSubject != null) {
            initiatedByExternalSubject = initiatedByExternalSubject.trim();

            if (initiatedByExternalSubject.isBlank()) {
                initiatedByExternalSubject = null;
            }
        }

        if (sourceWalletId.equals(destinationWalletId)) {
            throw new IllegalArgumentException(
                    "Source and destination wallets must be different."
            );
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException(
                    "amountMinor must be positive."
            );
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
