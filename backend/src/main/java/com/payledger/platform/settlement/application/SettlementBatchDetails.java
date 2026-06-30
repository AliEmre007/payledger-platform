package com.payledger.platform.settlement.application;

import com.payledger.platform.settlement.domain.SettlementBatch;

import java.util.UUID;

public record SettlementBatchDetails(
        UUID id,
        UUID merchantId,
        String currency,
        String status,
        long totalAmountMinor,
        UUID journalEntryId
) {

    public static SettlementBatchDetails from(SettlementBatch batch) {
        return new SettlementBatchDetails(
                batch.getId(),
                batch.getMerchantId(),
                batch.getCurrency(),
                batch.getStatus().name(),
                batch.getTotalAmountMinor(),
                batch.getJournalEntryId()
        );
    }
}
