package com.payledger.platform.provider.application;

public record ProviderWebhookResult(
        String providerEventId,
        String status,
        String ignoredReason
) {
}
