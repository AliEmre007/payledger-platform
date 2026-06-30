package com.payledger.platform.notification.infrastructure;

import com.payledger.platform.notification.application.NotificationDeliveryAdapter;
import com.payledger.platform.notification.application.NotificationDeliveryCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalLogNotificationDeliveryAdapter
        implements NotificationDeliveryAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            LocalLogNotificationDeliveryAdapter.class
    );

    @Override
    public void deliver(NotificationDeliveryCommand command) {
        if (command.forceFailure()) {
            throw new IllegalStateException(
                    "Simulated notification delivery failure."
            );
        }

        LOGGER.info(
                "Simulated notification delivered notificationId={} channel={} recipientType={} subject={}",
                command.notificationId(),
                command.channel(),
                command.recipientType(),
                command.subject()
        );
    }
}
