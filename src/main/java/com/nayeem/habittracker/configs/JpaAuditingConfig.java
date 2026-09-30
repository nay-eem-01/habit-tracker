package com.nayeem.habittracker.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Turns on {@code @CreatedDate}/{@code @CreatedBy} etc. Kept off the application class so web
 * slice tests don't need JPA.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    static final String SYSTEM = "SYSTEM";

    /**
     * The signed-in user's name (their email), or {@code SYSTEM}. Spring Security puts an
     * anonymous token on unauthenticated requests, so a null check alone is not enough — that
     * would audit a sign-up as {@code anonymousUser}.
     */
    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(currentAuditor(SecurityContextHolder.getContext().getAuthentication()));
    }

    static String currentAuditor(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        return authentication.getName();
    }
}
