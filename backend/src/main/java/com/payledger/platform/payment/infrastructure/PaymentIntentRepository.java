package com.payledger.platform.payment.infrastructure;

import com.payledger.platform.payment.domain.PaymentIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository
        extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByCustomerIdAndIdempotencyKey(
            UUID customerId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT intent
            FROM PaymentIntent intent
            WHERE intent.id = :paymentIntentId
            """)
    Optional<PaymentIntent> findByIdForUpdate(
            @Param("paymentIntentId") UUID paymentIntentId
    );
}
