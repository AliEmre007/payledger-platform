package com.payledger.platform.payment.infrastructure;

import com.payledger.platform.payment.domain.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository
        extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByCustomerIdAndIdempotencyKey(
            UUID customerId,
            String idempotencyKey
    );
}
