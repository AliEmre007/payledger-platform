package com.payledger.platform.ledger.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record PostJournalEntryCommand(
        String journalType,
        String referenceType,
        UUID referenceId,
        String currency,
        String description,
        Instant effectiveAt,
        List<LedgerPostingCommand> postings
) {
    public PostJournalEntryCommand {
        journalType = requiredText("journalType", journalType);
        referenceType = requiredText("referenceType", referenceType);
        Objects.requireNonNull(referenceId, "referenceId is required.");

        currency = requiredText("currency", currency).toUpperCase(Locale.ROOT);

        if (!currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(
                    "currency must be a three-letter ISO currency code."
            );
        }

        description = description == null || description.isBlank()
                ? null
                : description.trim();

        effectiveAt = effectiveAt == null ? Instant.now() : effectiveAt;

        postings = List.copyOf(Objects.requireNonNull(postings, "postings are required."));

        if (postings.size() < 2) {
            throw new IllegalArgumentException(
                    "A journal entry requires at least two postings."
            );
        }

        if (postings.size() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Too many ledger postings.");
        }
    }

    private static String requiredText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }

        return value.trim();
    }
}
