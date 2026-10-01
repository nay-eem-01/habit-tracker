package com.nayeem.habittracker.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Plain-text mail over SMTP. Only exists when {@code app.notifications.email.enabled=true}. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "true")
@RequiredArgsConstructor
class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    @Override
    public void send(OutgoingNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getEmail().getFrom());
        message.setTo(notification.email());
        message.setSubject(notification.title());
        message.setText(notification.body());
        mailSender.send(message);
        log.info("Email sent to user {}", notification.userId());
    }
}
