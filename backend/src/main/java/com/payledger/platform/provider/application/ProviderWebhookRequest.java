package com.payledger.platform.provider.application;

import java.util.UUID;

public record ProviderWebhookRequest(
        String eventId,
        String eventType,
        String providerTransactionId,
        UUID paymentIntentId
) {
}
