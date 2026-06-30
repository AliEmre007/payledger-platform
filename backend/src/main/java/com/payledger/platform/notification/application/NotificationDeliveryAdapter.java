package com.payledger.platform.notification.application;

public interface NotificationDeliveryAdapter {

    void deliver(NotificationDeliveryCommand command);
}
