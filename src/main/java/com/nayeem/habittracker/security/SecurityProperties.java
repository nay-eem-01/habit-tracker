package com.nayeem.habittracker.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** {@code app.security.*} — JWT and CORS settings. The secret comes from the environment only. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private final Jwt jwt = new Jwt();
    private final RefreshToken refreshToken = new RefreshToken();
    private final PasswordReset passwordReset = new PasswordReset();
    private final Cors cors = new Cors();
    private final RateLimits rateLimits = new RateLimits();
    private final Google google = new Google();

    @Getter
    @Setter
    public static class Jwt {
        /** HMAC-SHA key, at least 32 bytes. From {@code JWT_SECRET}; no default on purpose. */
        private String secret;
        private Duration accessTokenTtl = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class RefreshToken {
        private Duration ttl = Duration.ofDays(7);
        /** Secure cookie flag. Browsers accept Secure cookies on http://localhost, so keep it on. */
        private boolean cookieSecure = true;
    }

    @Getter
    @Setter
    public static class PasswordReset {
        /** How long a "forgot password" link works. */
        private Duration ttl = Duration.ofMinutes(30);
        /** At most this many reset emails per user per hour (and one per minute). */
        private int maxPerHour = 5;
    }

    @Getter
    @Setter
    public static class Cors {
        /** Exact origins; never {@code *} — the refresh cookie needs credentials. */
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class RateLimits {
        /** Per-IP limits on the public auth endpoints ({@link AuthRateLimitFilter}). Tests switch them off. */
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Google {
        /** The OAuth client id (not a secret); blank switches Google sign-in off. */
        private String clientId;
    }
}
