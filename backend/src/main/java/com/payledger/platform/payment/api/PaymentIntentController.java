package com.payledger.platform.payment.api;

import com.payledger.platform.identity.application.AuthenticatedCustomer;
import com.payledger.platform.identity.application.CurrentCustomerService;
import com.payledger.platform.payment.application.CreatePaymentIntentCommand;
import com.payledger.platform.payment.application.PaymentIntentService;
import com.payledger.platform.payment.domain.PaymentIntent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-intents")
public class PaymentIntentController {

    private final PaymentIntentService paymentIntentService;
    private final CurrentCustomerService currentCustomerService;

    public PaymentIntentController(
            PaymentIntentService paymentIntentService,
            CurrentCustomerService currentCustomerService
    ) {
        this.paymentIntentService = paymentIntentService;
        this.currentCustomerService = currentCustomerService;
    }

    @PostMapping
    public ResponseEntity<PaymentIntentResponse> authorize(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentIntentRequest request
    ) {
        AuthenticatedCustomer currentCustomer =
                currentCustomerService.getCurrentCustomer();

        PaymentIntent paymentIntent = paymentIntentService.authorize(
                new CreatePaymentIntentCommand(
                        currentCustomer.customerId(),
                        currentCustomer.externalSubject(),
                        request.sourceWalletId(),
                        request.merchantId(),
                        request.amountMinor(),
                        request.currency(),
                        idempotencyKey
                )
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PaymentIntentResponse.from(paymentIntent));
    }

    @PostMapping("/{paymentIntentId}/cancel")
    public PaymentIntentResponse cancel(
            @PathVariable UUID paymentIntentId
    ) {
        AuthenticatedCustomer currentCustomer =
                currentCustomerService.getCurrentCustomer();

        return PaymentIntentResponse.from(paymentIntentService.cancel(
                paymentIntentId,
                currentCustomer.customerId(),
                currentCustomer.externalSubject()
        ));
    }
}
