package com.payledger.platform.settlement.application;

import com.payledger.platform.settlement.domain.ReconciliationCase;

import java.util.UUID;

public record ReconciliationCaseDetails(
        UUID id,
        String status,
        long expectedAmountMinor,
        long actualAmountMinor,
        String expectedCurrency,
        String actualCurrency,
        String discrepancyReason
) {

    public static ReconciliationCaseDetails from(
            ReconciliationCase reconciliationCase
    ) {
        return new ReconciliationCaseDetails(
                reconciliationCase.getId(),
                reconciliationCase.getStatus().name(),
                reconciliationCase.getExpectedAmountMinor(),
                reconciliationCase.getActualAmountMinor(),
                reconciliationCase.getExpectedCurrency(),
                reconciliationCase.getActualCurrency(),
                reconciliationCase.getDiscrepancyReason()
        );
    }
}
