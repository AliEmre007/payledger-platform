package com.payledger.platform.risk.application;

public record RiskDecision(
        boolean allowed,
        String reasonCode
) {
    public static RiskDecision allow() {
        return new RiskDecision(true, "ALLOW");
    }
}
