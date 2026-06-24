package com.payledger.platform.identity.application;

import java.util.UUID;

public record AuthenticatedCustomer(
        UUID customerId,
        String externalSubject
) {
}
