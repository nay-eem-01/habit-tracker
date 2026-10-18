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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrevoNotificationSenderTest {

    private static final OutgoingNotification MESSAGE =
            new OutgoingNotification(7L, "reader@example.com", "Verify your email", "Open the link.");
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final AtomicReference<String> apiKey = new AtomicReference<>();
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

        sender("AveryDay <averydayhabits@gmail.com>", 201, meters).send(MESSAGE);

        assertEquals("xkeysib-test", apiKey.get());
        JsonNode sent = JSON.readTree(body.get());
        assertEquals("averydayhabits@gmail.com", sent.get("sender").get("email").asString());
        assertEquals("AveryDay", sent.get("sender").get("name").asString());
        assertEquals("reader@example.com", sent.get("to").get(0).get("email").asString());
        assertEquals("Verify your email", sent.get("subject").asString());
        assertEquals("Open the link.", sent.get("textContent").asString());
        assertEquals(1, meters.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "sent").count());
    }

    @Test
    void aBareFromAddressSendsNoName() throws IOException {
        sender("averydayhabits@gmail.com", 201, new SimpleMeterRegistry()).send(MESSAGE);

        assertFalse(JSON.readTree(body.get()).get("sender").has("name"));
    }

    @Test
    void aRefusedEmailIsCountedAndThrown() throws IOException {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        var sender = sender("averydayhabits@gmail.com", 401, meters);

        assertThrows(IllegalStateException.class, () -> sender.send(MESSAGE));
        assertEquals(1, meters.counter(AppMetrics.EMAILS, AppMetrics.OUTCOME, "failed").count());
    }

    @Test
    void brevoWithoutAnApiKeyRefusesToStart() {
        contextRunner()
                .withPropertyValues("app.notifications.email.from=averydayhabits@gmail.com")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void brevoWithAnInvalidFromRefusesToStart() {
        contextRunner()
                .withPropertyValues("app.notifications.email.from=AveryDay <not an address",
                        "app.notifications.email.brevo.api-key=xkeysib-test")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void brevoProviderReplacesSmtp() {
        contextRunner()
                .withPropertyValues("app.notifications.email.from=averydayhabits@gmail.com",
                        "app.notifications.email.brevo.api-key=xkeysib-test")
                .run(context -> assertThat(context).hasSingleBean(BrevoNotificationSender.class)
                        .doesNotHaveBean(EmailNotificationSender.class)
                        .doesNotHaveBean(ResendNotificationSender.class)
                        .doesNotHaveBean(ConsoleNotificationSender.class));
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(NotificationProperties.class, EmailNotificationSender.class,
                        BrevoNotificationSender.class, ResendNotificationSender.class,
                        ConsoleNotificationSender.class)
                .withBean(ObjectMapper.class, () -> JSON)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("app.notifications.email.enabled=true",
                        "app.notifications.email.provider=brevo");
    }

    private BrevoNotificationSender sender(String from, int status, MeterRegistry meters) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        NotificationProperties properties = new NotificationProperties();
        properties.getEmail().setFrom(from);
        properties.getEmail().getBrevo().setApiKey("xkeysib-test");
        properties.getEmail().getBrevo().setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
        BrevoNotificationSender sender = new BrevoNotificationSender(properties, JSON, meters);
        sender.init();
        return sender;
    }
}
