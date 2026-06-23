package com.payledger.platform.wallet.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateWalletRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$")
        String currency
) {
}
