package com.payledger.platform.operations.api;

import com.payledger.platform.operations.application.OperationsReadService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasAnyRole('OPERATIONS', 'ADMIN')")
public class OperationsReadController {

    private final OperationsReadService operationsReadService;

    public OperationsReadController(
            OperationsReadService operationsReadService
    ) {
        this.operationsReadService = operationsReadService;
    }

    @GetMapping("/audit-events")
    public OperationPageResponse<OperationalAuditEventResponse> auditEvents(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return operationsReadService.auditEvents(
                actionType,
                resourceType,
                page,
                size
        );
    }

    @GetMapping("/customers")
    public OperationPageResponse<OperationalCustomerResponse> customers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kycStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return operationsReadService.customers(
                status,
                kycStatus,
                page,
                size
        );
    }

    @GetMapping("/wallets")
    public OperationPageResponse<OperationalWalletResponse> wallets(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return operationsReadService.wallets(
                customerId,
                status,
                page,
                size
        );
    }

    @GetMapping("/payment-intents")
    public OperationPageResponse<OperationalPaymentIntentResponse> paymentIntents(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return operationsReadService.paymentIntents(
                customerId,
                merchantId,
                status,
                page,
                size
        );
    }

    @GetMapping("/reconciliation-cases")
    public OperationPageResponse<OperationalReconciliationCaseResponse> reconciliationCases(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return operationsReadService.reconciliationCases(
                merchantId,
                status,
                page,
                size
        );
    }
}
