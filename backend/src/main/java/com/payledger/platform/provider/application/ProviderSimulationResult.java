package com.payledger.platform.provider.application;

import com.payledger.platform.provider.domain.ProviderTransactionStatus;

import java.util.UUID;

public record ProviderSimulationResult(
        String providerName,
        String providerTransactionId,
        UUID paymentIntentId,
        ProviderTransactionStatus requestedOutcome
) {
}
