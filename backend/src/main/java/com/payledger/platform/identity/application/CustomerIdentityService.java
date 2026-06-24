package com.payledger.platform.identity.application;

import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.identity.domain.CustomerIdentity;
import com.payledger.platform.identity.domain.IdentityProvider;
import com.payledger.platform.identity.infrastructure.CustomerIdentityRepository;
import com.payledger.platform.shared.error.ConflictException;
import com.payledger.platform.shared.error.IdentityNotLinkedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerIdentityService {

    private final CustomerService customerService;
    private final CustomerIdentityRepository customerIdentityRepository;

    public CustomerIdentityService(
            CustomerService customerService,
            CustomerIdentityRepository customerIdentityRepository
    ) {
        this.customerService = customerService;
        this.customerIdentityRepository = customerIdentityRepository;
    }

    @Transactional
    public CustomerIdentity linkKeycloakIdentity(
            UUID customerId,
            String externalSubject
    ) {
        customerService.getCustomer(customerId);

        String normalizedSubject = normalizeSubject(externalSubject);

        CustomerIdentity existingForSubject =
                customerIdentityRepository
                        .findByIdentityProviderAndExternalSubject(
                                IdentityProvider.KEYCLOAK,
                                normalizedSubject
                        )
                        .orElse(null);

        if (existingForSubject != null) {
            if (existingForSubject.getCustomerId().equals(customerId)) {
                return existingForSubject;
            }

            throw new ConflictException(
                    "This Keycloak subject is already linked to another customer."
            );
        }

        CustomerIdentity existingForCustomer =
                customerIdentityRepository
                        .findByCustomerIdAndIdentityProvider(
                                customerId,
                                IdentityProvider.KEYCLOAK
                        )
                        .orElse(null);

        if (existingForCustomer != null) {
            throw new ConflictException(
                    "This customer is already linked to a different Keycloak identity."
            );
        }

        return customerIdentityRepository.saveAndFlush(
                CustomerIdentity.linkKeycloak(
                        customerId,
                        normalizedSubject
                )
        );
    }

    @Transactional(readOnly = true)
    public AuthenticatedCustomer resolveKeycloakSubject(
            String externalSubject
    ) {
        String normalizedSubject = normalizeSubject(externalSubject);

        CustomerIdentity identity = customerIdentityRepository
                .findByIdentityProviderAndExternalSubject(
                        IdentityProvider.KEYCLOAK,
                        normalizedSubject
                )
                .orElseThrow(() -> new IdentityNotLinkedException(
                        "The authenticated Keycloak identity is not linked to a PayLedger customer."
                ));

        return new AuthenticatedCustomer(
                identity.getCustomerId(),
                identity.getExternalSubject()
        );
    }

    private String normalizeSubject(String externalSubject) {
        if (externalSubject == null) {
            throw new IllegalArgumentException(
                    "externalSubject is required."
            );
        }

        String normalized = externalSubject.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "externalSubject must not be blank."
            );
        }

        return normalized;
    }
}
