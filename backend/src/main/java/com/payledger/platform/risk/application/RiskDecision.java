package com.payledger.platform.risk.application;

public record RiskDecision(
        boolean allowed,
        String reasonCode
) {
    public static RiskDecision allow() {
        return new RiskDecision(true, "ALLOW");
    }

    public static RiskDecision deny(String reasonCode) {
        return new RiskDecision(false, reasonCode);
    }
}
