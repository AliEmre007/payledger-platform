package com.payledger.platform.operations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkKeycloakIdentityRequest(
        @NotBlank @Size(max = 255) String externalSubject,
        @NotBlank @Size(max = 500) String reason
) {
}
