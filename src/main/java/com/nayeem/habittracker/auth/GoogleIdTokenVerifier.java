package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.security.SecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Checks a Google ID token (Sign in with Google, ID-token flow — PLAN.md §3.2) locally: Google's
 * signature (keys from its JWKS, cached by Nimbus), {@code iss} Google, {@code aud} our client id, not
 * expired. No call to Google per sign-in beyond refreshing the keys.
 */
@Component
@RequiredArgsConstructor
class GoogleIdTokenVerifier {

    static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final SecurityProperties properties;

    private NimbusJwtDecoder decoder;

    @PostConstruct
    void init() {
        decoder = withGoogleRules(NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build(),
                properties.getGoogle().getClientId());
    }

    /** @throws ApplicationException 401 {@code AUTH_INVALID_GOOGLE_TOKEN} for anything that isn't a valid token for us */
    GoogleIdentity verify(String idToken) {
        if (!StringUtils.hasText(properties.getGoogle().getClientId())) {
            throw new ApplicationException(ErrorCode.AUTH_GOOGLE_DISABLED);
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new ApplicationException(ErrorCode.AUTH_INVALID_GOOGLE_TOKEN);
        }
        Object verified = jwt.getClaims().get("email_verified");
        return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(verified) || "true".equals(verified), jwt.getClaimAsString("name"));
    }

    /** The checks on top of the signature; separate so a test can apply them to a decoder with its own key. */
    static NimbusJwtDecoder withGoogleRules(NimbusJwtDecoder decoder, String clientId) {
        OAuth2TokenValidator<Jwt> issuer = jwt -> GOOGLE_ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not issued by Google", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not for this app", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience));
        return decoder;
    }
}
