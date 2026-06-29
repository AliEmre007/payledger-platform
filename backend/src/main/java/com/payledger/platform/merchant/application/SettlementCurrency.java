package com.payledger.platform.merchant.application;

import com.payledger.platform.merchant.domain.MerchantSettlementConfig;

public record SettlementCurrency(
        String currency,
        int settlementDelayDays,
        boolean enabled
) {
    public static SettlementCurrency from(
            MerchantSettlementConfig config
    ) {
        return new SettlementCurrency(
                config.getCurrency(),
                config.getSettlementDelayDays(),
                config.isEnabled()
        );
    }
}
