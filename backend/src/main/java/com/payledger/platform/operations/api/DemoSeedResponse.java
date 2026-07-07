package com.payledger.platform.operations.api;

import com.payledger.platform.operations.application.DemoSeedResult;

import java.util.UUID;

public record DemoSeedResponse(
        UUID customerId,
        UUID customerWalletId,
        long customerWalletBalanceMinor,
        UUID recipientCustomerId,
        UUID recipientWalletId,
        UUID merchantId,
        String currency,
        boolean topUpCreated
) {
    public static DemoSeedResponse from(DemoSeedResult result) {
        return new DemoSeedResponse(
                result.customerId(),
                result.customerWalletId(),
                result.customerWalletBalanceMinor(),
                result.recipientCustomerId(),
                result.recipientWalletId(),
                result.merchantId(),
                result.currency(),
                result.topUpCreated()
        );
    }
}
