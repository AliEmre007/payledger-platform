package com.payledger.platform.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void transferCompleted(String currency) {
        counter(
                "payledger_transfers_completed_total",
                "currency",
                normalize(currency)
        ).increment();
    }

    public void paymentStateTransition(String status) {
        counter(
                "payledger_payment_state_transitions_total",
                "status",
                normalize(status)
        ).increment();
    }

    public void riskDenied(String action, String reasonCode) {
        counter(
                "payledger_risk_denials_total",
                "action",
                normalize(action),
                "reason",
                normalize(reasonCode)
        ).increment();
    }

    public void notificationProcessed(String status) {
        counter(
                "payledger_notifications_processed_total",
                "status",
                normalize(status)
        ).increment();
    }

    public void reconciliationDiscrepancy(String reason) {
        counter(
                "payledger_reconciliation_discrepancies_total",
                "reason",
                normalize(reason)
        ).increment();
    }

    public void rateLimited(String path) {
        counter(
                "payledger_rate_limited_requests_total",
                "path",
                normalizePath(path)
        ).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }

        return value.trim().toUpperCase();
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "UNKNOWN";
        }

        return path.trim();
    }
}
