package com.payledger.platform.operations.application;

import java.util.UUID;

public record DemoSeedResult(
        UUID customerId,
        UUID customerWalletId,
        long customerWalletBalanceMinor,
        UUID recipientCustomerId,
        UUID recipientWalletId,
        UUID merchantId,
        String currency,
        boolean topUpCreated
) {
}
