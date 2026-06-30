package com.payledger.platform.settlement.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconcileSettlementRequest(
        @NotBlank @Size(max = 120) String providerReference,
        @Min(0) long actualAmountMinor,
        @NotBlank @Size(min = 3, max = 3) String actualCurrency
) {
}
