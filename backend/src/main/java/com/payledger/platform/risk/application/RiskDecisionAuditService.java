package com.payledger.platform.risk.application;

import com.payledger.platform.audit.application.AuditEventCommand;
import com.payledger.platform.audit.application.AuditEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class RiskDecisionAuditService {

    private final AuditEventService auditEventService;

    public RiskDecisionAuditService(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenial(
            String action,
            UUID customerId,
            UUID sourceWalletId,
            UUID destinationWalletId,
            UUID merchantId,
            long amountMinor,
            String currency,
            String actorExternalSubject,
            String reasonCode
    ) {
        auditEventService.record(
                new AuditEventCommand(
                        "RISK_DECISION_DENIED",
                        actorExternalSubject,
                        customerId,
                        "RISK_DECISION",
                        UUID.randomUUID(),
                        Map.of(
                                "action", action,
                                "reasonCode", reasonCode,
                                "customerId", customerId.toString(),
                                "sourceWalletId", sourceWalletId.toString(),
                                "destinationWalletId", destinationWalletId == null
                                        ? ""
                                        : destinationWalletId.toString(),
                                "merchantId", merchantId == null
                                        ? ""
                                        : merchantId.toString(),
                                "amountMinor", amountMinor,
                                "currency", currency
                        )
                )
        );
    }
}
