package com.payledger.platform.provider.infrastructure;

import com.payledger.platform.provider.domain.ProviderTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProviderTransactionRepository
        extends JpaRepository<ProviderTransaction, UUID> {

    Optional<ProviderTransaction> findByPaymentIntentId(UUID paymentIntentId);

    Optional<ProviderTransaction> findByProviderNameAndProviderTransactionId(
            String providerName,
            String providerTransactionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT transaction
            FROM ProviderTransaction transaction
            WHERE transaction.providerName = :providerName
              AND transaction.providerTransactionId = :providerTransactionId
            """)
    Optional<ProviderTransaction> findByProviderTransactionForUpdate(
            @Param("providerName") String providerName,
            @Param("providerTransactionId") String providerTransactionId
    );
}
