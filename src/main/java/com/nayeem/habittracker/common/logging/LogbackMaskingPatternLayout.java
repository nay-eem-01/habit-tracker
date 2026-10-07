package com.nayeem.habittracker.common.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Last line of defence for secrets that reach a log line anyway (a DTO without a masking
 * {@code toString()}, a header or cookie logged by a library, an exception message). The sensitive
 * DTOs still mask themselves; this catches what slips past them. Wired into every appender in
 * {@code logback-spring.xml}.
 */
public class LogbackMaskingPatternLayout extends PatternLayout {

    // Any key containing password / token / secret / credential:
    // "password":"x", "newPassword":"x", "refreshToken":"x", "idToken":"x", "secret":"x"
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\"\\w*(?:password|token|secret|credential)\\w*\"\\s*:\\s*\")[^\"]*(\")");

    // password=x, currentPassword=x, refresh_token=x (cookie headers), ?token=x (reset links), secret=x
    private static final Pattern KV_SECRET = Pattern.compile(
            "(?i)(\\w*(?:password|token|secret|credential)\\w*=)[^,;&}\\])\\s]+");

    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9_\\-.]+");

    // Google access tokens and anything JWT-shaped (our access tokens, Google ID tokens)
    private static final Pattern OAUTH_TOKEN = Pattern.compile(
            "ya29\\.[A-Za-z0-9_\\-.]{10,}|ey[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+");

    @Override
    public String doLayout(ILoggingEvent event) {
        String m = super.doLayout(event);
        if (m == null) return null;
        m = JSON_SECRET.matcher(m).replaceAll("$1******$2");
        m = KV_SECRET.matcher(m).replaceAll("$1******");
        m = BEARER.matcher(m).replaceAll("$1******");
        return OAUTH_TOKEN.matcher(m).replaceAll("******");
    }
}
