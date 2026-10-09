package com.nayeem.habittracker.push;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

/**
 * Sends one encrypted message to one subscription (RFC 8030), signed with our VAPID key (RFC 8292).
 * Returns the push service's status: 201 delivered, 404/410 the subscription is gone.
 */
@Component
@RequiredArgsConstructor
class WebPushSender {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    private final PushProperties properties;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private ECPrivateKey vapidKey;

    /** Fails startup when push is on without a usable key pair. */
    @PostConstruct
    void init() throws GeneralSecurityException {
        if (!properties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.getPublicKey()) || !StringUtils.hasText(properties.getPrivateKey())
                || !StringUtils.hasText(properties.getSubject())) {
            throw new IllegalStateException("app.push.enabled needs VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY and VAPID_SUBJECT");
        }
        WebPushCrypto.publicKey(B64.decode(properties.getPublicKey()));
        vapidKey = WebPushCrypto.privateKey(B64.decode(properties.getPrivateKey()));
    }

    int send(PushSubscription subscription, byte[] payload)
            throws GeneralSecurityException, IOException, InterruptedException {
        byte[] body = WebPushCrypto.encrypt(payload, B64.decode(subscription.getP256dh()),
                B64.decode(subscription.getAuth()));
        URI endpoint = URI.create(subscription.getEndpoint());
        String origin = endpoint.getScheme() + "://" + endpoint.getAuthority();
        String jwt = Jwts.builder()
                .header().type("JWT").and()
                .audience().single(origin)
                .expiration(Date.from(clock.instant().plus(Duration.ofHours(12))))
                .subject(properties.getSubject())
                .signWith(vapidKey, Jwts.SIG.ES256)
                .compact();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("TTL", "86400")
                .header("Urgency", "normal")
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", "vapid t=" + jwt + ", k=" + properties.getPublicKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
