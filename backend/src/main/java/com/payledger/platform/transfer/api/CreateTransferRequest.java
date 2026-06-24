package com.payledger.platform.transfer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateTransferRequest(

        @NotNull
        UUID sourceWalletId,

        @NotNull
        UUID destinationWalletId,

        @NotNull
        UUID initiatedByCustomerId,

        @Positive
        long amountMinor,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$")
        String currency
) {
}
