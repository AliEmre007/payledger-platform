package com.payledger.platform.merchant.api;

import com.payledger.platform.merchant.application.MerchantDetails;

import java.util.List;
import java.util.UUID;

public record PublicMerchantResponse(
        UUID id,
        String displayName,
        List<String> currencies
) {
    public static PublicMerchantResponse from(MerchantDetails merchant) {
        return new PublicMerchantResponse(
                merchant.id(),
                merchant.displayName(),
                merchant.settlementCurrencies()
                        .stream()
                        .filter(settlementCurrency -> settlementCurrency.enabled())
                        .map(settlementCurrency -> settlementCurrency.currency())
                        .toList()
        );
    }
}
