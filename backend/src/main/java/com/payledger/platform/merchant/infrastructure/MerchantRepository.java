package com.payledger.platform.merchant.infrastructure;

import com.payledger.platform.merchant.domain.Merchant;
import com.payledger.platform.merchant.domain.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    boolean existsByDisplayNameIgnoreCase(String displayName);

    Optional<Merchant> findByIdAndStatus(
            UUID merchantId,
            MerchantStatus status
    );
}
