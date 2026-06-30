package com.payledger.platform.identity.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import com.payledger.platform.customer.application.CustomerService;
import com.payledger.platform.identity.domain.CustomerIdentity;
import com.payledger.platform.identity.domain.IdentityProvider;
import com.payledger.platform.identity.infrastructure.CustomerIdentityRepository;
import com.payledger.platform.outbox.application.OutboxEventCommand;
import com.payledger.platform.outbox.application.OutboxEventService;
import com.payledger.platform.shared.error.ConflictException;
import com.payledger.platform.shared.error.IdentityNotLinkedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CustomerIdentityService {

    private final CustomerService customerService;
    private final CustomerIdentityRepository customerIdentityRepository;
    private final AuditEventService auditEventService;
    private final OutboxEventService outboxEventService;

    public CustomerIdentityService(
            CustomerService customerService,
            CustomerIdentityRepository customerIdentityRepository,
            AuditEventService auditEventService,
            OutboxEventService outboxEventService
    ) {
        this.customerService = customerService;
        this.customerIdentityRepository = customerIdentityRepository;
        this.auditEventService = auditEventService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public CustomerIdentity linkKeycloakIdentity(
            UUID customerId,
            String externalSubject
    ) {
        return linkKeycloakIdentity(
                customerId,
                externalSubject,
                normalizeSubject(externalSubject),
                null
        );
    }

    @Transactional
    public CustomerIdentity linkKeycloakIdentity(
            UUID customerId,
            String externalSubject,
            String actorExternalSubject,
            String reason
    ) {
        customerService.getCustomer(customerId);

        String normalizedSubject = normalizeSubject(externalSubject);
        String normalizedActor = normalizeSubject(actorExternalSubject);

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

        CustomerIdentity identity = customerIdentityRepository.saveAndFlush(
                CustomerIdentity.linkKeycloak(
                        customerId,
                        normalizedSubject
                )
        );

        Map<String, Object> metadata = Map.of(
                "identityProvider", identity.getIdentityProvider().name(),
                "linkedExternalSubject", identity.getExternalSubject(),
                "reason", reason == null ? "" : reason.trim()
        );

        auditEventService.record(
                new AuditEventCommand(
                        "CUSTOMER_IDENTITY_LINKED",
                        normalizedActor,
                        customerId,
                        "CUSTOMER_IDENTITY",
                        identity.getId(),
                        metadata
                )
        );

        outboxEventService.enqueue(
                new OutboxEventCommand(
                        "CUSTOMER_IDENTITY_LINKED",
                        "CUSTOMER_IDENTITY",
                        identity.getId(),
                        Map.of(
                                "customerId", customerId.toString(),
                                "identityProvider",
                                identity.getIdentityProvider().name()
                        )
                )
        );

        return identity;
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
