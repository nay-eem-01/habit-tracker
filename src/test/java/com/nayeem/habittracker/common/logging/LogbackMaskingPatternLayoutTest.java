package com.nayeem.habittracker.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackMaskingPatternLayoutTest {

    private final LoggerContext context = new LoggerContext();
    private final LogbackMaskingPatternLayout layout = new LogbackMaskingPatternLayout();

    @BeforeEach
    void setUp() {
        layout.setContext(context);
        layout.setPattern("%m");
        layout.start();
    }

    private String render(String message) {
        LoggingEvent event = new LoggingEvent("test", context.getLogger("test"), Level.INFO, message, null, null);
        return layout.doLayout(event);
    }

    @Test
    void masksSecretsInJson() {
        assertThat(render("{\"email\":\"a@b.com\",\"password\":\"hunter2\",\"refreshToken\":\"abc\",\"currentPassword\":\"old\"}"))
                .isEqualTo("{\"email\":\"a@b.com\",\"password\":\"******\",\"refreshToken\":\"******\",\"currentPassword\":\"******\"}");
    }

    @Test
    void masksSecretsInKeyValuePairs() {
        assertThat(render("Request[email=a@b.com, newPassword=hunter2, token=r3s3t]"))
                .isEqualTo("Request[email=a@b.com, newPassword=******, token=******]");
    }

    @Test
    void masksRefreshCookieAndResetLink() {
        assertThat(render("Set-Cookie: refresh_token=abc.def; Path=/api/auth; HttpOnly"))
                .isEqualTo("Set-Cookie: refresh_token=******; Path=/api/auth; HttpOnly");
        assertThat(render("link http://localhost:5173/reset-password?token=XyZ123&x=1"))
                .isEqualTo("link http://localhost:5173/reset-password?token=******&x=1");
    }

    @Test
    void masksBearerAndJwtShapedTokens() {
        assertThat(render("Authorization: Bearer abc.def.ghi")).isEqualTo("Authorization: Bearer ******");
        assertThat(render("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhIn0.c2lnbmF0dXJl here"))
                .isEqualTo("token ****** here");
    }

    @Test
    void leavesOrdinaryMessagesAlone() {
        String message = "Habit 42 archived by 7 (habitId=42, completedCount=3)";
        assertThat(render(message)).isEqualTo(message);
    }
}
