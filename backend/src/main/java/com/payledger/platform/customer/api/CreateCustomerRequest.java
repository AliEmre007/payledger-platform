package com.payledger.platform.customer.api;

import com.payledger.platform.customer.domain.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(

        @NotNull
        CustomerType customerType,

        @NotBlank
        @Size(max = 255)
        String legalName,

        @NotBlank
        @Email
        @Size(max = 320)
        String email
) {
}
