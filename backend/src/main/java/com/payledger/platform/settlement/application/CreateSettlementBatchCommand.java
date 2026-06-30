package com.payledger.platform.settlement.application;

import java.util.UUID;

public record CreateSettlementBatchCommand(
        UUID merchantId,
        String currency,
        String idempotencyKey,
        String actorExternalSubject,
        String reason
) {
}
