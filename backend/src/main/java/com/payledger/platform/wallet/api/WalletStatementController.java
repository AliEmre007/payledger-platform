package com.payledger.platform.wallet.api;

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

    public WalletStatementController(
            WalletStatementService walletStatementService
    ) {
        this.walletStatementService = walletStatementService;
    }

    @GetMapping("/{walletId}/statement")
    public WalletStatementResponse getStatement(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return WalletStatementResponse.from(
                walletStatementService.getStatement(
                        walletId,
                        page,
                        size
                )
        );
    }
}
