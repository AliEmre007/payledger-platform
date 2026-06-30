package com.payledger.platform.operations.api;

import com.payledger.platform.identity.domain.CustomerIdentity;

import java.util.UUID;

public record CustomerIdentityResponse(
        UUID id,
        UUID customerId,
        String identityProvider,
        String externalSubject
) {

    public static CustomerIdentityResponse from(CustomerIdentity identity) {
        return new CustomerIdentityResponse(
                identity.getId(),
                identity.getCustomerId(),
                identity.getIdentityProvider().name(),
                identity.getExternalSubject()
        );
    }
}
