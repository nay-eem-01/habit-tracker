package com.nayeem.habittracker.security;

import com.nayeem.habittracker.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Limits the public auth endpoints per client IP, so passwords can't be guessed at full speed and
 * sign-ups and reset emails can't be scripted. Over the limit: 429 {@code RATE_LIMITED} with
 * {@code Retry-After}. Behind a proxy the IP comes from {@code X-Forwarded-For}
 * ({@code server.forward-headers-strategy=native}, prod profile).
 */
@RequiredArgsConstructor
class AuthRateLimitFilter extends OncePerRequestFilter {

    record Limit(int requests, Duration window) {
    }

    static final Map<String, Limit> LIMITS = Map.of(
            "/api/auth/login", new Limit(10, Duration.ofMinutes(1)),
            "/api/auth/register", new Limit(10, Duration.ofHours(1)),
            "/api/auth/refresh", new Limit(30, Duration.ofMinutes(1)),
            "/api/auth/password/forgot", new Limit(5, Duration.ofHours(1)),
            "/api/auth/password/reset", new Limit(10, Duration.ofHours(1)));

    private final RateLimiter rateLimiter;
    private final JsonSecurityErrorHandler errorWriter;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Limit limit = "POST".equals(request.getMethod()) ? LIMITS.get(request.getRequestURI()) : null;
        if (limit != null) {
            String key = request.getRequestURI() + ":" + request.getRemoteAddr();
            if (!rateLimiter.tryAcquire(key, limit.requests(), limit.window())) {
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimiter.secondsLeft(key)));
                errorWriter.write(response, ErrorCode.RATE_LIMITED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
