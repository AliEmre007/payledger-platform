package com.payledger.platform.operations.api;

import com.payledger.platform.customer.api.CustomerResponse;
import com.payledger.platform.customer.domain.Customer;
import com.payledger.platform.kyc.application.KycOperationsService;
import com.payledger.platform.operations.application.CurrentOperationActorService;
import com.payledger.platform.operations.application.OperationActor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/customers")
@PreAuthorize("hasAnyRole('OPERATIONS', 'ADMIN')")
public class OperationsCustomerController {

    private final KycOperationsService kycOperationsService;
    private final CurrentOperationActorService currentOperationActorService;

    public OperationsCustomerController(
            KycOperationsService kycOperationsService,
            CurrentOperationActorService currentOperationActorService
    ) {
        this.kycOperationsService = kycOperationsService;
        this.currentOperationActorService = currentOperationActorService;
    }

    @PostMapping("/{customerId}/kyc/submit")
    public CustomerResponse submitForReview(
            @PathVariable UUID customerId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentOperationActorService.getCurrentActor();
        Customer customer = kycOperationsService.submitForReview(
                customerId,
                actor.externalSubject(),
                request.reason()
        );

        return CustomerResponse.from(customer);
    }

    @PostMapping("/{customerId}/kyc/approve")
    public CustomerResponse approve(
            @PathVariable UUID customerId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentOperationActorService.getCurrentActor();
        Customer customer = kycOperationsService.approve(
                customerId,
                actor.externalSubject(),
                request.reason()
        );

        return CustomerResponse.from(customer);
    }

    @PostMapping("/{customerId}/kyc/reject")
    public CustomerResponse reject(
            @PathVariable UUID customerId,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentOperationActorService.getCurrentActor();
        Customer customer = kycOperationsService.reject(
                customerId,
                actor.externalSubject(),
                request.reason()
        );

        return CustomerResponse.from(customer);
    }
}
