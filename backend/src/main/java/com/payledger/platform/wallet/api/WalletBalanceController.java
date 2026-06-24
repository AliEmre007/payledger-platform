package com.payledger.platform.wallet.api;

import com.payledger.platform.identity.application.AuthenticatedCustomer;
import com.payledger.platform.identity.application.CurrentCustomerService;
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
    private final CurrentCustomerService currentCustomerService;

    public WalletBalanceController(
            WalletBalanceService walletBalanceService,
            CurrentCustomerService currentCustomerService
    ) {
        this.walletBalanceService = walletBalanceService;
        this.currentCustomerService = currentCustomerService;
    }

    @GetMapping("/{walletId}/balance")
    public WalletBalanceResponse getBalance(
            @PathVariable UUID walletId
    ) {
        AuthenticatedCustomer currentCustomer =
                currentCustomerService.getCurrentCustomer();

        return WalletBalanceResponse.from(
                walletBalanceService.getBalance(
                        walletId,
                        currentCustomer.customerId()
                )
        );
    }
}
