package com.payledger.platform.operations.api;

import com.payledger.platform.operations.application.CurrentOperationActorService;
import com.payledger.platform.operations.application.OperationActor;
import com.payledger.platform.wallet.api.WalletResponse;
import com.payledger.platform.wallet.application.WalletLifecycleService;
import com.payledger.platform.wallet.domain.Wallet;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/wallets")
@PreAuthorize("hasAnyRole('OPERATIONS', 'ADMIN')")
public class OperationsWalletController {

    private final WalletLifecycleService walletLifecycleService;
    private final CurrentOperationActorService currentOperationActorService;

    public OperationsWalletController(
            WalletLifecycleService walletLifecycleService,
            CurrentOperationActorService currentOperationActorService
    ) {
        this.walletLifecycleService = walletLifecycleService;
        this.currentOperationActorService = currentOperationActorService;
    }

    @PostMapping("/{walletId}/freeze")
    public WalletResponse freeze(
            @PathVariable UUID walletId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentOperationActorService.getCurrentActor();
        Wallet wallet = walletLifecycleService.freeze(
                walletId,
                actor.externalSubject(),
                request.reason()
        );

        return WalletResponse.from(wallet);
    }

    @PostMapping("/{walletId}/unfreeze")
    public WalletResponse unfreeze(
            @PathVariable UUID walletId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentOperationActorService.getCurrentActor();
        Wallet wallet = walletLifecycleService.unfreeze(
                walletId,
                actor.externalSubject(),
                request.reason()
        );

        return WalletResponse.from(wallet);
    }

    @PostMapping("/{walletId}/close")
    public WalletResponse close(
            @PathVariable UUID walletId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentOperationActorService.getCurrentActor();
        Wallet wallet = walletLifecycleService.close(
                walletId,
                actor.externalSubject(),
                request.reason()
        );

        return WalletResponse.from(wallet);
    }
}
