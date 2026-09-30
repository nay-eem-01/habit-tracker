package com.nayeem.habittracker.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-at-least-32-bytes-long";

    @Test
    void roundTripsTheEmail() {
        JwtService jwtService = jwtService(SECRET);
        String token = jwtService.generateAccessToken(42L, "a@b.com");

        assertThat(jwtService.extractValidEmail(token)).contains("a@b.com");
        var claims = Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(token).getPayload();
        assertThat(claims.get(JwtService.USER_ID_CLAIM, Long.class)).isEqualTo(42L);
        assertThat(claims.getExpiration().toInstant())
                .isBetween(Instant.now().plusSeconds(14 * 60), Instant.now().plusSeconds(15 * 60 + 5));
    }

    @Test
    void rejectsExpiredToken() {
        String expired = Jwts.builder().subject("a@b.com")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key(SECRET)).compact();
        assertThat(jwtService(SECRET).extractValidEmail(expired)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String forged = jwtService("another-secret-that-is-32-bytes-long!!").generateAccessToken(1L, "a@b.com");
        assertThat(jwtService(SECRET).extractValidEmail(forged)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(jwtService(SECRET).extractValidEmail("not.a.jwt")).isEmpty();
    }

    @Test
    void refusesToStartWithoutAStrongSecret() {
        assertThatThrownBy(() -> jwtService(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> jwtService("${JWT_SECRET}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> jwtService("too-short")).isInstanceOf(IllegalStateException.class);
    }

    private static JwtService jwtService(String secret) {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret(secret);
        JwtService jwtService = new JwtService(properties);
        jwtService.initSigningKey();
        return jwtService;
    }

    private static javax.crypto.SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
