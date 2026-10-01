package com.nayeem.habittracker.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The default channel: no mail server needed, just a log line (user id only — no address, no text). */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "false", matchIfMissing = true)
class ConsoleNotificationSender implements NotificationSender {

    @Override
    public void send(OutgoingNotification notification) {
        log.info("Email disabled; would have emailed user {}", notification.userId());
    }
}
