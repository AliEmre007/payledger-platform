package com.payledger.platform.wallet.infrastructure;

import com.payledger.platform.wallet.domain.FundsHold;
import com.payledger.platform.wallet.domain.FundsHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FundsHoldRepository
        extends JpaRepository<FundsHold, UUID> {

    Optional<FundsHold> findByWalletIdAndIdempotencyKey(
            UUID walletId,
            String idempotencyKey
    );

    Optional<FundsHold> findByWalletIdAndReferenceTypeAndReferenceId(
            UUID walletId,
            String referenceType,
            UUID referenceId
    );

    @Query("""
            SELECT COALESCE(SUM(hold.amountMinor), 0)
            FROM FundsHold hold
            WHERE hold.walletId = :walletId
              AND hold.status = :status
            """)
    long sumAmountMinorByWalletIdAndStatus(
            @Param("walletId") UUID walletId,
            @Param("status") FundsHoldStatus status
    );
}
