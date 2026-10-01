package com.nayeem.habittracker.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** {@code app.notifications.*}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

    private final Email email = new Email();

    @Getter
    @Setter
    public static class Email {
        /** Off: reminders are only logged. On: sent over SMTP (needs {@code spring.mail.host}). */
        private boolean enabled;
        /** The From address. */
        private String from;
    }
}
