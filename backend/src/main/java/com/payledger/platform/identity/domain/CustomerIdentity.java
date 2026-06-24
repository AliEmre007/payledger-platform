package com.payledger.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customer_identities")
public class CustomerIdentity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_provider", nullable = false, length = 30, updatable = false)
    private IdentityProvider identityProvider;

    @Column(name = "external_subject", nullable = false, length = 255, updatable = false)
    private String externalSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CustomerIdentity() {
    }

    private CustomerIdentity(
            UUID customerId,
            IdentityProvider identityProvider,
            String externalSubject
    ) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.identityProvider = identityProvider;
        this.externalSubject = externalSubject;
        this.createdAt = Instant.now();
    }

    public static CustomerIdentity linkKeycloak(
            UUID customerId,
            String externalSubject
    ) {
        Objects.requireNonNull(customerId, "customerId is required.");

        String normalizedSubject = Objects.requireNonNull(
                externalSubject,
                "externalSubject is required."
        ).trim();

        if (normalizedSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "externalSubject must not be blank."
            );
        }

        if (normalizedSubject.length() > 255) {
            throw new IllegalArgumentException(
                    "externalSubject must not exceed 255 characters."
            );
        }

        return new CustomerIdentity(
                customerId,
                IdentityProvider.KEYCLOAK,
                normalizedSubject
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public IdentityProvider getIdentityProvider() {
        return identityProvider;
    }

    public String getExternalSubject() {
        return externalSubject;
    }
}
