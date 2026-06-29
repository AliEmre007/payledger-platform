package com.payledger.platform.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreatePaymentIntentRequest(

        @NotNull
        UUID sourceWalletId,

        @NotNull
        UUID merchantId,

        @Positive
        long amountMinor,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$")
        String currency
) {
}
