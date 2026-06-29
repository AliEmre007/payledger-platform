package com.payledger.platform.merchant.application;

public record OnboardMerchantCommand(
        String legalName,
        String displayName,
        String settlementCurrency,
        int settlementDelayDays,
        String actorExternalSubject,
        String reason
) {
}
