package com.nayeem.habittracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies access tokens (plan §4.1): {@code sub} = email, {@code uid} = user id,
 * short-lived, sent as a Bearer header, never stored. No other claims — a JWT is signed, not
 * encrypted.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    static final String USER_ID_CLAIM = "uid";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecurityProperties properties;

    private SecretKey signingKey;

    /** Fails startup rather than run with a missing or weak secret. */
    @PostConstruct
    void initSigningKey() {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.contains("${") || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be set (JWT_SECRET) and be at least " + MIN_SECRET_BYTES + " bytes");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim(USER_ID_CLAIM, userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getJwt().getAccessTokenTtl())))
                .signWith(signingKey)
                .compact();
    }

    /** The email of a token with a valid signature that hasn't expired; empty for anything else. */
    public Optional<String> extractValidEmail(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.getJwt().getAccessTokenTtl().toSeconds();
    }
}
