package com.payledger.platform.operations.api;

import com.payledger.platform.operations.application.CurrentOperationActorService;
import com.payledger.platform.operations.application.OperationActor;
import com.payledger.platform.payment.api.PaymentIntentResponse;
import com.payledger.platform.payment.application.PaymentIntentService;
import com.payledger.platform.payment.domain.PaymentIntent;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/payment-intents")
@PreAuthorize("hasAnyRole('OPERATIONS', 'ADMIN')")
public class OperationsPaymentIntentController {

    private final PaymentIntentService paymentIntentService;
    private final CurrentOperationActorService currentActorService;

    public OperationsPaymentIntentController(
            PaymentIntentService paymentIntentService,
            CurrentOperationActorService currentActorService
    ) {
        this.paymentIntentService = paymentIntentService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/{paymentIntentId}/capture")
    public PaymentIntentResponse capture(
            @PathVariable UUID paymentIntentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();
        PaymentIntent paymentIntent = paymentIntentService.capture(
                paymentIntentId,
                idempotencyKey,
                actor.externalSubject(),
                request.reason()
        );

        return PaymentIntentResponse.from(paymentIntent);
    }

    @PostMapping("/{paymentIntentId}/refund")
    public PaymentIntentResponse refund(
            @PathVariable UUID paymentIntentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OperationReasonRequest request
    ) {
        OperationActor actor = currentActorService.getCurrentActor();
        PaymentIntent paymentIntent = paymentIntentService.refund(
                paymentIntentId,
                idempotencyKey,
                actor.externalSubject(),
                request.reason()
        );

        return PaymentIntentResponse.from(paymentIntent);
    }
}
