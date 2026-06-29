package com.payledger.platform.merchant.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OnboardMerchantRequest(

        @NotBlank
        @Size(max = 255)
        String legalName,

        @NotBlank
        @Size(max = 120)
        String displayName,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$")
        String settlementCurrency,

        @Min(0)
        @Max(30)
        int settlementDelayDays,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
