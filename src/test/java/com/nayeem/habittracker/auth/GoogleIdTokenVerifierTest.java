package com.nayeem.habittracker.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The checks on a Google ID token besides Google's own key, with a local key standing in for Google's. */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "our-client.apps.googleusercontent.com";
    private final KeyPair google = rsa();
    private final NimbusJwtDecoder decoder = GoogleIdTokenVerifier.withGoogleRules(
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) google.getPublic()).build(), CLIENT_ID);

    @Test
    void acceptsAGoogleTokenForUs() throws Exception {
        assertThat(decoder.decode(token(google, "https://accounts.google.com", CLIENT_ID, 300)).getSubject())
                .isEqualTo("1234567890");
        assertThat(decoder.decode(token(google, "accounts.google.com", CLIENT_ID, 300)).getSubject())
                .isEqualTo("1234567890");
    }

    @Test
    void refusesAnotherAudienceIssuerKeyOrAnExpiredToken() throws Exception {
        assertThatThrownBy(() -> decoder.decode(token(google, "https://accounts.google.com", "someone-else", 300)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(google, "https://evil.example.com", CLIENT_ID, 300)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(rsa(), "https://accounts.google.com", CLIENT_ID, 300)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(google, "https://accounts.google.com", CLIENT_ID, -300)))
                .isInstanceOf(JwtException.class);
    }

    private static String token(KeyPair key, String issuer, String audience, long expiresInSeconds) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer).audience(audience).subject("1234567890")
                .claim("email", "someone@gmail.com").claim("email_verified", true)
                .issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
