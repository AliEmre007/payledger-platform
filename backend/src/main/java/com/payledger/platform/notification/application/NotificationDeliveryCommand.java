package com.payledger.platform.notification.application;

import java.util.UUID;

public record NotificationDeliveryCommand(
        UUID notificationId,
        String channel,
        String recipientType,
        String recipientReference,
        String subject,
        String body,
        boolean forceFailure
) {
}
