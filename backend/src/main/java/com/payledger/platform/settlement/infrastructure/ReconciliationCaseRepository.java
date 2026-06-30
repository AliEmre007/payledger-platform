package com.payledger.platform.settlement.infrastructure;

import com.payledger.platform.settlement.domain.ReconciliationCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReconciliationCaseRepository
        extends JpaRepository<ReconciliationCase, UUID> {

    Optional<ReconciliationCase> findByProviderReference(
            String providerReference
    );
}
