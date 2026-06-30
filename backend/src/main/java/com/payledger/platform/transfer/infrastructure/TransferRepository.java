package com.payledger.platform.transfer.infrastructure;

import com.payledger.platform.transfer.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findBySourceWalletIdAndIdempotencyKey(
            UUID sourceWalletId,
            String idempotencyKey
    );

    long countBySourceWalletIdAndIdempotencyKey(
            UUID sourceWalletId,
            String idempotencyKey
    );

    @Query("""
            SELECT COALESCE(SUM(transfer.amountMinor), 0)
            FROM Transfer transfer
            WHERE transfer.initiatedByCustomerId = :customerId
              AND transfer.currency = :currency
              AND transfer.createdAt >= :startInclusive
              AND transfer.createdAt < :endExclusive
            """)
    long sumOutgoingAmountForCustomer(
            @Param("customerId") UUID customerId,
            @Param("currency") String currency,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );

    @Query("""
            SELECT COUNT(transfer)
            FROM Transfer transfer
            WHERE transfer.initiatedByCustomerId = :customerId
              AND transfer.currency = :currency
              AND transfer.createdAt >= :startInclusive
              AND transfer.createdAt < :endExclusive
            """)
    long countOutgoingForCustomer(
            @Param("customerId") UUID customerId,
            @Param("currency") String currency,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );
}
