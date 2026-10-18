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
        /** Off: reminders are only logged. On: sent by {@link #provider}. */
        private boolean enabled;
        /**
         * {@code smtp} (needs {@code spring.mail.host}), or an HTTPS API: {@code brevo} (needs {@link #brevo})
         * or {@code resend} (needs {@link #resend}).
         */
        private String provider = "smtp";
        /** The From address. */
        private String from;
        private final Brevo brevo = new Brevo();
        private final Resend resend = new Resend();
    }

    @Getter
    @Setter
    public static class Brevo {
        /** Secret. */
        private String apiKey;
        private String url = "https://api.brevo.com/v3/smtp/email";
    }

    @Getter
    @Setter
    public static class Resend {
        /** Secret. */
        private String apiKey;
        private String url = "https://api.resend.com/emails";
    }
}
