package com.payledger.platform.operations.api;

import com.payledger.platform.operations.application.CurrentOperationActorService;
import com.payledger.platform.operations.application.OperationActor;
import com.payledger.platform.settlement.api.CreateSettlementBatchRequest;
import com.payledger.platform.settlement.api.ReconcileSettlementRequest;
import com.payledger.platform.settlement.api.ReconciliationCaseResponse;
import com.payledger.platform.settlement.api.SettlementBatchResponse;
import com.payledger.platform.settlement.application.CreateSettlementBatchCommand;
import com.payledger.platform.settlement.application.ReconcileSettlementCommand;
import com.payledger.platform.settlement.application.ReconciliationCaseDetails;
import com.payledger.platform.settlement.application.SettlementBatchDetails;
import com.payledger.platform.settlement.application.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/settlements")
@PreAuthorize("hasAnyRole('OPERATIONS', 'ADMIN')")
public class OperationsSettlementController {

    private final SettlementService settlementService;
    private final CurrentOperationActorService currentActorService;

    public OperationsSettlementController(
            SettlementService settlementService,
            CurrentOperationActorService currentActorService
    ) {
        this.settlementService = settlementService;
        this.currentActorService = currentActorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementBatchResponse createBatch(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateSettlementBatchRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();
        SettlementBatchDetails details = settlementService.createBatch(
                new CreateSettlementBatchCommand(
                        request.merchantId(),
                        request.currency(),
                        idempotencyKey,
                        actor.externalSubject(),
                        request.reason()
                )
        );

        return SettlementBatchResponse.from(details);
    }

    @PostMapping("/{settlementBatchId}/reconcile")
    public ReconciliationCaseResponse reconcile(
            @PathVariable UUID settlementBatchId,
            @Valid @RequestBody ReconcileSettlementRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();
        ReconciliationCaseDetails details = settlementService.reconcile(
                new ReconcileSettlementCommand(
                        settlementBatchId,
                        request.providerReference(),
                        request.actualAmountMinor(),
                        request.actualCurrency(),
                        actor.externalSubject(),
                        request.reason()
                )
        );

        return ReconciliationCaseResponse.from(details);
    }
}
