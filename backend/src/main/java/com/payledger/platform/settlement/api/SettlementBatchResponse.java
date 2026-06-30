package com.payledger.platform.settlement.api;

import com.payledger.platform.settlement.application.SettlementBatchDetails;

import java.util.UUID;

public record SettlementBatchResponse(
        UUID id,
        UUID merchantId,
        String currency,
        String status,
        long totalAmountMinor,
        UUID journalEntryId
) {

    public static SettlementBatchResponse from(
            SettlementBatchDetails details
    ) {
        return new SettlementBatchResponse(
                details.id(),
                details.merchantId(),
                details.currency(),
                details.status(),
                details.totalAmountMinor(),
                details.journalEntryId()
        );
    }
}
