package com.payledger.platform.merchant.infrastructure;

import com.payledger.platform.merchant.domain.MerchantSettlementConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantSettlementConfigRepository
        extends JpaRepository<MerchantSettlementConfig, UUID> {

    List<MerchantSettlementConfig> findByMerchantId(UUID merchantId);

    Optional<MerchantSettlementConfig> findByMerchantIdAndCurrency(
            UUID merchantId,
            String currency
    );
}
