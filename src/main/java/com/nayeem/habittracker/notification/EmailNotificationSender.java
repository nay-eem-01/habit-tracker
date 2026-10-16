package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.common.AppMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Plain-text mail over SMTP. Only exists when email is enabled with provider {@code smtp} (the default). */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "true")
@ConditionalOnProperty(name = "app.notifications.email.provider", havingValue = "smtp", matchIfMissing = true)
@RequiredArgsConstructor
class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;

    @Override
    public void send(OutgoingNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getEmail().getFrom());
        message.setTo(notification.email());
        message.setSubject(notification.title());
        message.setText(notification.body());
        try {
            mailSender.send(message);
        } catch (RuntimeException e) {
            meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").increment();
            throw e;
        }
        meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "sent").increment();
        log.info("Email sent to user {}", notification.userId());
    }
}
