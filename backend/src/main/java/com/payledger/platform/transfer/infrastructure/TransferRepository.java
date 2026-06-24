package com.payledger.platform.transfer.infrastructure;

import com.payledger.platform.transfer.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
