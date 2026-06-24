package com.payledger.platform.identity.infrastructure;

import com.payledger.platform.identity.domain.CustomerIdentity;
import com.payledger.platform.identity.domain.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentityRepository
        extends JpaRepository<CustomerIdentity, UUID> {

    Optional<CustomerIdentity> findByIdentityProviderAndExternalSubject(
            IdentityProvider identityProvider,
            String externalSubject
    );

    Optional<CustomerIdentity> findByCustomerIdAndIdentityProvider(
            UUID customerId,
            IdentityProvider identityProvider
    );
}
