package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.common.AppMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-text mail over Brevo's HTTPS API — for hosts that block SMTP ports (Render's free tier).
 * Only exists when email is enabled with provider {@code brevo}. Brevo sends from a single verified
 * sender address (no domain needed) and delivers to anyone, up to its free daily limit.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "true")
@ConditionalOnProperty(name = "app.notifications.email.provider", havingValue = "brevo")
@RequiredArgsConstructor
class BrevoNotificationSender implements NotificationSender {

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    /** {@code {"email", "name"}} from the From address, which Brevo takes as an object. */
    private Map<String, String> sender;

    /** Fails startup when Brevo is on without a key or a valid From address. */
    @PostConstruct
    void init() {
        if (!StringUtils.hasText(properties.getEmail().getBrevo().getApiKey())
                || !StringUtils.hasText(properties.getEmail().getFrom())) {
            throw new IllegalStateException("Brevo email needs BREVO_API_KEY and APP_NOTIFICATIONS_EMAIL_FROM");
        }
        InternetAddress from;
        try {
            from = new InternetAddress(properties.getEmail().getFrom(), true);
        } catch (AddressException e) {
            throw new IllegalStateException("APP_NOTIFICATIONS_EMAIL_FROM is not an email address", e);
        }
        sender = new LinkedHashMap<>();
        sender.put("email", from.getAddress());
        if (StringUtils.hasText(from.getPersonal())) {
            sender.put("name", from.getPersonal());
        }
    }

    @Override
    public void send(OutgoingNotification notification) {
        String json = objectMapper.writeValueAsString(Map.of(
                "sender", sender,
                "to", List.of(Map.of("email", notification.email())),
                "subject", notification.title(),
                "textContent", notification.body()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEmail().getBrevo().getUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("api-key", properties.getEmail().getBrevo().getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        int status;
        try {
            status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").increment();
            throw new IllegalStateException("Brevo unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").increment();
            throw new IllegalStateException("Interrupted while sending", e);
        }
        if (status / 100 != 2) {
            meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").increment();
            // status only: Brevo's error text can carry an address
            log.warn("Brevo refused the email to user {} with HTTP {}", notification.userId(), status);
            throw new IllegalStateException("Brevo answered HTTP " + status);
        }
        meterRegistry.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "sent").increment();
        log.info("Email sent to user {}", notification.userId());
    }
}
