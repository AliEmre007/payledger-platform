package com.payledger.platform.risk.application;

import java.util.UUID;

public record PaymentAuthorizationRiskRequest(
        UUID customerId,
        UUID walletId,
        UUID merchantId,
        long amountMinor,
        String currency
) {
}
