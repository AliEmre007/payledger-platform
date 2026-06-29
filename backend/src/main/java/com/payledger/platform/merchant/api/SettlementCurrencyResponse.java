package com.payledger.platform.merchant.api;

import com.payledger.platform.merchant.application.SettlementCurrency;

public record SettlementCurrencyResponse(
        String currency,
        int settlementDelayDays,
        boolean enabled
) {
    public static SettlementCurrencyResponse from(
            SettlementCurrency settlementCurrency
    ) {
        return new SettlementCurrencyResponse(
                settlementCurrency.currency(),
                settlementCurrency.settlementDelayDays(),
                settlementCurrency.enabled()
        );
    }
}
