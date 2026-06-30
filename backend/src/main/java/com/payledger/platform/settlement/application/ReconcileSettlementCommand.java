package com.payledger.platform.settlement.application;

import java.util.UUID;

public record ReconcileSettlementCommand(
        UUID settlementBatchId,
        String providerReference,
        long actualAmountMinor,
        String actualCurrency,
        String actorExternalSubject,
        String reason
) {
}
