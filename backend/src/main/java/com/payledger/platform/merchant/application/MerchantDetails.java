package com.payledger.platform.merchant.application;

import com.payledger.platform.merchant.domain.Merchant;
import com.payledger.platform.merchant.domain.MerchantStatus;

import java.util.List;
import java.util.UUID;

public record MerchantDetails(
        UUID id,
        String legalName,
        String displayName,
        MerchantStatus status,
        List<SettlementCurrency> settlementCurrencies
) {
    public static MerchantDetails from(
            Merchant merchant,
            List<SettlementCurrency> settlementCurrencies
    ) {
        return new MerchantDetails(
                merchant.getId(),
                merchant.getLegalName(),
                merchant.getDisplayName(),
                merchant.getStatus(),
                settlementCurrencies
        );
    }
}
