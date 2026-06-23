package com.payledger.platform.wallet.api;

import com.payledger.platform.wallet.domain.Wallet;
import com.payledger.platform.wallet.domain.WalletStatus;

import java.util.UUID;

public record WalletResponse(
        UUID id,
        UUID customerId,
        String currency,
        WalletStatus status
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getCustomerId(),
                wallet.getCurrency(),
                wallet.getStatus()
        );
    }
}
