package com.payledger.platform.settlement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSettlementBatchRequest(
        @NotNull UUID merchantId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank @Size(max = 500) String reason
) {
}
