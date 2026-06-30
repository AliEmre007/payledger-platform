package com.payledger.platform.notification.application;

public record NotificationMessage(
        String recipientType,
        String recipientReference,
        String subject,
        String body,
        boolean forceFailure
) {
}
