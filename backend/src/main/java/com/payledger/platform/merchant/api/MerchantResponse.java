package com.payledger.platform.merchant.api;

import com.payledger.platform.merchant.application.MerchantDetails;
import com.payledger.platform.merchant.domain.MerchantStatus;

import java.util.List;
import java.util.UUID;

public record MerchantResponse(
        UUID id,
        String legalName,
        String displayName,
        MerchantStatus status,
        List<SettlementCurrencyResponse> settlementCurrencies
) {
    public static MerchantResponse from(MerchantDetails merchant) {
        return new MerchantResponse(
                merchant.id(),
                merchant.legalName(),
                merchant.displayName(),
                merchant.status(),
                merchant.settlementCurrencies()
                        .stream()
                        .map(SettlementCurrencyResponse::from)
                        .toList()
        );
    }
}
