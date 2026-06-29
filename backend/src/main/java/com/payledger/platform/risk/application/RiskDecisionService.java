package com.payledger.platform.risk.application;

import org.springframework.stereotype.Service;

@Service
public class RiskDecisionService {

    public RiskDecision assessPaymentAuthorization(
            PaymentAuthorizationRiskRequest request
    ) {
        return RiskDecision.allow();
    }
}
