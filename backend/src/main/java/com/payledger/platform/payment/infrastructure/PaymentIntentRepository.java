package com.payledger.platform.payment.infrastructure;

import com.payledger.platform.payment.domain.PaymentIntent;
import com.payledger.platform.payment.domain.PaymentIntentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT intent
            FROM PaymentIntent intent
            WHERE intent.merchantId = :merchantId
              AND intent.currency = :currency
              AND intent.status = :status
              AND intent.captureJournalEntryId IS NOT NULL
              AND NOT EXISTS (
                    SELECT line.id
                    FROM SettlementLine line
                    WHERE line.paymentIntentId = intent.id
              )
            ORDER BY intent.capturedAt ASC, intent.id ASC
            """)
    List<PaymentIntent> findUnsettledCapturedForUpdate(
            @Param("merchantId") UUID merchantId,
            @Param("currency") String currency,
            @Param("status") PaymentIntentStatus status
    );
}
