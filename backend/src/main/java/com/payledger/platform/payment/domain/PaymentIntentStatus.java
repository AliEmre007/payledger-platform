package com.payledger.platform.payment.domain;

public enum PaymentIntentStatus {
    CREATED,
    AUTHORIZED,
    CAPTURED,
    CANCELED,
    EXPIRED,
    FAILED,
    REFUNDED
}
