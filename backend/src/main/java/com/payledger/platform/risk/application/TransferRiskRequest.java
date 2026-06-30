package com.payledger.platform.risk.application;

import java.util.UUID;

public record TransferRiskRequest(
        UUID customerId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        long amountMinor,
        String currency
) {
}
