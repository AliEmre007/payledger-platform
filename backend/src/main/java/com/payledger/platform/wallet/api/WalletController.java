package com.payledger.platform.wallet.api;

import com.payledger.platform.wallet.application.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/customers/{customerId}/wallets")
    public ResponseEntity<WalletResponse> createWallet(
            @PathVariable UUID customerId,
            @Valid @RequestBody CreateWalletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(WalletResponse.from(
                        walletService.createWallet(customerId, request.currency())
                ));
    }

    @GetMapping("/wallets/{walletId}")
    public WalletResponse getWallet(@PathVariable UUID walletId) {
        return WalletResponse.from(walletService.getWallet(walletId));
    }
}
