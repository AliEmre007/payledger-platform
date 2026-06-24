package com.payledger.platform.wallet.api;

import com.payledger.platform.wallet.application.WalletBalanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletBalanceController {

    private final WalletBalanceService walletBalanceService;

    public WalletBalanceController(
            WalletBalanceService walletBalanceService
    ) {
        this.walletBalanceService = walletBalanceService;
    }

    @GetMapping("/{walletId}/balance")
    public WalletBalanceResponse getBalance(
            @PathVariable UUID walletId
    ) {
        return WalletBalanceResponse.from(
                walletBalanceService.getBalance(walletId)
        );
    }
}
