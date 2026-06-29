package com.payledger.platform.payment.api;

import com.payledger.platform.payment.domain.PaymentIntent;
import com.payledger.platform.payment.domain.PaymentIntentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(
        UUID id,
        UUID sourceWalletId,
        UUID merchantId,
        UUID fundsHoldId,
        UUID captureJournalEntryId,
        UUID refundJournalEntryId,
        long amountMinor,
        String currency,
        PaymentIntentStatus status,
        Instant createdAt,
        Instant authorizedAt,
        Instant canceledAt,
        Instant capturedAt,
        Instant refundedAt
) {
    public static PaymentIntentResponse from(PaymentIntent paymentIntent) {
        return new PaymentIntentResponse(
                paymentIntent.getId(),
                paymentIntent.getSourceWalletId(),
                paymentIntent.getMerchantId(),
                paymentIntent.getFundsHoldId(),
                paymentIntent.getCaptureJournalEntryId(),
                paymentIntent.getRefundJournalEntryId(),
                paymentIntent.getAmountMinor(),
                paymentIntent.getCurrency(),
                paymentIntent.getStatus(),
                paymentIntent.getCreatedAt(),
                paymentIntent.getAuthorizedAt(),
                paymentIntent.getCanceledAt(),
                paymentIntent.getCapturedAt(),
                paymentIntent.getRefundedAt()
        );
    }
}
