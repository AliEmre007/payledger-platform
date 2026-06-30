package com.payledger.platform.notification.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class OutboxNotificationMapper {

    public Optional<NotificationMessage> map(
            String eventType,
            String aggregateType,
            String aggregateId,
            Map<String, Object> payload
    ) {
        boolean forceFailure = Boolean.TRUE.equals(
                payload.get("forceDeliveryFailure")
        );

        return switch (eventType) {
            case "INTERNAL_TRANSFER_COMPLETED" -> Optional.of(customerMessage(
                    payload,
                    "Transfer completed",
                    "Your wallet transfer has completed.",
                    forceFailure
            ));
            case "PAYMENT_INTENT_AUTHORIZED" -> Optional.of(customerMessage(
                    payload,
                    "Payment authorized",
                    "Your wallet payment has been authorized.",
                    forceFailure
            ));
            case "PAYMENT_INTENT_CAPTURED" -> Optional.of(customerMessage(
                    payload,
                    "Payment captured",
                    "Your authorized wallet payment has been captured.",
                    forceFailure
            ));
            case "PAYMENT_INTENT_REFUNDED" -> Optional.of(customerMessage(
                    payload,
                    "Payment refunded",
                    "Your wallet payment has been refunded.",
                    forceFailure
            ));
            case "KYC_APPROVED" -> Optional.of(customerMessage(
                    payload,
                    "KYC approved",
                    "Your KYC review has been approved.",
                    forceFailure
            ));
            case "KYC_REJECTED" -> Optional.of(customerMessage(
                    payload,
                    "KYC rejected",
                    "Your KYC review was not approved.",
                    forceFailure
            ));
            case "WALLET_FROZEN" -> Optional.of(customerMessage(
                    payload,
                    "Wallet frozen",
                    "A wallet has been frozen by operations.",
                    forceFailure
            ));
            default -> Optional.empty();
        };
    }

    private NotificationMessage customerMessage(
            Map<String, Object> payload,
            String subject,
            String body,
            boolean forceFailure
    ) {
        Object customerId = payload.get("customerId");

        return new NotificationMessage(
                customerId == null ? "OPERATIONS" : "CUSTOMER",
                customerId == null ? "operations" : customerId.toString(),
                subject,
                body,
                forceFailure
        );
    }
}
