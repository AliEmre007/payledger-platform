package com.payledger.platform.operations.api;

import com.payledger.platform.merchant.api.MerchantResponse;
import com.payledger.platform.merchant.api.OnboardMerchantRequest;
import com.payledger.platform.merchant.application.MerchantService;
import com.payledger.platform.merchant.application.OnboardMerchantCommand;
import com.payledger.platform.operations.application.CurrentOperationActorService;
import com.payledger.platform.operations.application.OperationActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/merchants")
@PreAuthorize("hasAnyRole('OPERATIONS', 'ADMIN')")
public class OperationsMerchantController {

    private final MerchantService merchantService;
    private final CurrentOperationActorService currentActorService;

    public OperationsMerchantController(
            MerchantService merchantService,
            CurrentOperationActorService currentActorService
    ) {
        this.merchantService = merchantService;
        this.currentActorService = currentActorService;
    }

    @PostMapping
    public ResponseEntity<MerchantResponse> onboard(
            @Valid @RequestBody OnboardMerchantRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MerchantResponse.from(merchantService.onboard(
                        new OnboardMerchantCommand(
                                request.legalName(),
                                request.displayName(),
                                request.settlementCurrency(),
                                request.settlementDelayDays(),
                                actor.externalSubject(),
                                request.reason()
                        )
                )));
    }

    @PostMapping("/{merchantId}/activate")
    public MerchantResponse activate(
            @PathVariable UUID merchantId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();

        return MerchantResponse.from(merchantService.activate(
                merchantId,
                actor.externalSubject(),
                request.reason()
        ));
    }

    @PostMapping("/{merchantId}/suspend")
    public MerchantResponse suspend(
            @PathVariable UUID merchantId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();

        return MerchantResponse.from(merchantService.suspend(
                merchantId,
                actor.externalSubject(),
                request.reason()
        ));
    }

    @PostMapping("/{merchantId}/close")
    public MerchantResponse close(
            @PathVariable UUID merchantId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();

        return MerchantResponse.from(merchantService.close(
                merchantId,
                actor.externalSubject(),
                request.reason()
        ));
    }
}
