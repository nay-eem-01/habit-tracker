package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.common.AppMetrics;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResendNotificationSenderTest {

    private static final OutgoingNotification MESSAGE =
            new OutgoingNotification(7L, "reader@example.com", "Verify your email", "Open the link.");
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsTheEmailWithTheApiKey() throws IOException {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();

        sender(200, meters).send(MESSAGE);

        assertEquals("Bearer re_test", authorization.get());
        JsonNode sent = JSON.readTree(body.get());
        assertEquals("onboarding@resend.dev", sent.get("from").asString());
        assertEquals("reader@example.com", sent.get("to").get(0).asString());
        assertEquals("Verify your email", sent.get("subject").asString());
        assertEquals("Open the link.", sent.get("text").asString());
        assertEquals(1, meters.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "sent").count());
    }

    @Test
    void aRefusedEmailIsCountedAndThrown() throws IOException {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        var sender = sender(403, meters);

        assertThrows(IllegalStateException.class, () -> sender.send(MESSAGE));
        assertEquals(1, meters.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").count());
    }

    @Test
    void resendWithoutAnApiKeyRefusesToStart() {
        contextRunner()
                .withPropertyValues("app.notifications.email.from=onboarding@resend.dev")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void resendProviderReplacesSmtp() {
        contextRunner()
                .withPropertyValues("app.notifications.email.from=onboarding@resend.dev",
                        "app.notifications.email.resend.api-key=re_test")
                .run(context -> assertThat(context).hasSingleBean(ResendNotificationSender.class)
                        .doesNotHaveBean(EmailNotificationSender.class)
                        .doesNotHaveBean(ConsoleNotificationSender.class));
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(NotificationProperties.class, EmailNotificationSender.class,
                        ResendNotificationSender.class, ConsoleNotificationSender.class)
                .withBean(ObjectMapper.class, () -> JSON)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("app.notifications.email.enabled=true",
                        "app.notifications.email.provider=resend");
    }

    private ResendNotificationSender sender(int status, MeterRegistry meters) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        NotificationProperties properties = new NotificationProperties();
        properties.getEmail().setFrom("onboarding@resend.dev");
        properties.getEmail().getResend().setApiKey("re_test");
        properties.getEmail().getResend().setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/emails");
        ResendNotificationSender sender = new ResendNotificationSender(properties, JSON, meters);
        sender.init();
        return sender;
    }
}
