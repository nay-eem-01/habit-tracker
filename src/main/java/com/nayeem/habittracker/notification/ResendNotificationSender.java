package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.common.AppMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Plain-text mail over Resend's HTTPS API — for hosts that block SMTP ports (Render's free tier).
 * Only exists when email is enabled with provider {@code resend}. Without a verified domain the From
 * must be {@code onboarding@resend.dev}, and Resend then only delivers to the account owner's address.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "true")
@ConditionalOnProperty(name = "app.notifications.email.provider", havingValue = "resend")
@RequiredArgsConstructor
class ResendNotificationSender implements NotificationSender {

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** Fails startup when Resend is on without a key or a From address. */
    @PostConstruct
    void init() {
        if (!StringUtils.hasText(properties.getEmail().getResend().getApiKey())
                || !StringUtils.hasText(properties.getEmail().getFrom())) {
            throw new IllegalStateException("Resend email needs RESEND_API_KEY and APP_NOTIFICATIONS_EMAIL_FROM");
        }
    }

    @Override
    public void send(OutgoingNotification notification) {
        String json = objectMapper.writeValueAsString(Map.of(
                "from", properties.getEmail().getFrom(),
                "to", List.of(notification.email()),
                "subject", notification.title(),
                "text", notification.body()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEmail().getResend().getUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.getEmail().getResend().getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        int status;
        try {
            status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").increment();
            throw new IllegalStateException("Resend unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").increment();
            throw new IllegalStateException("Interrupted while sending", e);
        }
        if (status / 100 != 2) {
            meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").increment();
            // status only: Resend's error text can carry an address
            log.warn("Resend refused the email to user {} with HTTP {}", notification.userId(), status);
            throw new IllegalStateException("Resend answered HTTP " + status);
        }
        meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "sent").increment();
        log.info("Email sent to user {}", notification.userId());
    }
}
