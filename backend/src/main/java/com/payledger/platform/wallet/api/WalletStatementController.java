package com.payledger.platform.wallet.api;

import com.payledger.platform.identity.application.AuthenticatedCustomer;
import com.payledger.platform.identity.application.CurrentCustomerService;
import com.payledger.platform.wallet.application.WalletStatementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletStatementController {

    private final WalletStatementService walletStatementService;
    private final CurrentCustomerService currentCustomerService;

    public WalletStatementController(
            WalletStatementService walletStatementService,
            CurrentCustomerService currentCustomerService
    ) {
        this.walletStatementService = walletStatementService;
        this.currentCustomerService = currentCustomerService;
    }

    @GetMapping("/{walletId}/statement")
    public WalletStatementResponse getStatement(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        AuthenticatedCustomer currentCustomer =
                currentCustomerService.getCurrentCustomer();

        return WalletStatementResponse.from(
                walletStatementService.getStatement(
                        walletId,
                        currentCustomer.customerId(),
                        page,
                        size
                )
        );
    }
}
