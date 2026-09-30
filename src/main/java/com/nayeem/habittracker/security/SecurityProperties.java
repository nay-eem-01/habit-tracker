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
    private final Cors cors = new Cors();

    @Getter
    @Setter
    public static class Jwt {
        /** HMAC-SHA key, at least 32 bytes. From {@code JWT_SECRET}; no default on purpose. */
        private String secret;
        private Duration accessTokenTtl = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class Cors {
        /** Exact origins; never {@code *} — the refresh cookie needs credentials. */
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
