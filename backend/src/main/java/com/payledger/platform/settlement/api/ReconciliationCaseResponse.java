package com.payledger.platform.settlement.api;

import com.payledger.platform.settlement.application.ReconciliationCaseDetails;

import java.util.UUID;

public record ReconciliationCaseResponse(
        UUID id,
        String status,
        long expectedAmountMinor,
        long actualAmountMinor,
        String expectedCurrency,
        String actualCurrency,
        String discrepancyReason
) {

    public static ReconciliationCaseResponse from(
            ReconciliationCaseDetails details
    ) {
        return new ReconciliationCaseResponse(
                details.id(),
                details.status(),
                details.expectedAmountMinor(),
                details.actualAmountMinor(),
                details.expectedCurrency(),
                details.actualCurrency(),
                details.discrepancyReason()
        );
    }
}
