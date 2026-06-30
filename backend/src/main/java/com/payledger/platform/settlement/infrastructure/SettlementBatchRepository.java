package com.payledger.platform.settlement.infrastructure;

import com.payledger.platform.settlement.domain.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementBatchRepository
        extends JpaRepository<SettlementBatch, UUID> {

    Optional<SettlementBatch> findByMerchantIdAndIdempotencyKey(
            UUID merchantId,
            String idempotencyKey
    );
}
