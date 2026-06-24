package com.payledger.platform.ledger.application;

import com.payledger.platform.ledger.domain.PostingDirection;

import java.util.Objects;
import java.util.UUID;

public record LedgerPostingCommand(
        UUID ledgerAccountId,
        PostingDirection direction,
        long amountMinor
) {
    public LedgerPostingCommand {
        Objects.requireNonNull(ledgerAccountId, "ledgerAccountId is required.");
        Objects.requireNonNull(direction, "direction is required.");

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive.");
        }
    }
}
