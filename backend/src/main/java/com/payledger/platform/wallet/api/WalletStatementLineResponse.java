package com.payledger.platform.wallet.api;

import com.payledger.platform.ledger.domain.PostingDirection;
import com.payledger.platform.wallet.application.WalletStatementLine;

import java.time.Instant;
import java.util.UUID;

public record WalletStatementLineResponse(
        UUID postingId,
        UUID journalEntryId,
        String journalType,
        String referenceType,
        UUID referenceId,
        String description,
        Instant occurredAt,
        PostingDirection direction,
        long amountMinor,
        long signedAmountMinor,
        String currency,
        int lineNumber
) {
    public static WalletStatementLineResponse from(
            WalletStatementLine line
    ) {
        return new WalletStatementLineResponse(
                line.postingId(),
                line.journalEntryId(),
                line.journalType(),
                line.referenceType(),
                line.referenceId(),
                line.description(),
                line.occurredAt(),
                line.direction(),
                line.amountMinor(),
                line.signedAmountMinor(),
                line.currency(),
                line.lineNumber()
        );
    }
}
