package com.nayeem.habittracker.push;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** One message to a local stand-in push service: the VAPID header and the encrypted body's framing. */
class WebPushSenderTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private HttpServer server;

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void postsAnEncryptedBodySignedWithTheVapidKey() throws Exception {
        AtomicReference<Headers> headers = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/push/abc", exchange -> {
            headers.set(exchange.getRequestHeaders());
            body.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();

        KeyPair vapid = p256();
        PushProperties properties = new PushProperties();
        properties.setEnabled(true);
        properties.setPublicKey(B64.encodeToString(WebPushCrypto.encodePoint((ECPublicKey) vapid.getPublic())));
        properties.setPrivateKey(B64.encodeToString(unsigned32(((ECPrivateKey) vapid.getPrivate()).getS().toByteArray())));
        properties.setSubject("mailto:ops@example.com");
        WebPushSender sender = new WebPushSender(properties, Clock.systemUTC());
        sender.init();

        PushSubscription subscription = new PushSubscription();
        String origin = "http://localhost:" + server.getAddress().getPort();
        subscription.setEndpoint(origin + "/push/abc");
        subscription.setP256dh(B64.encodeToString(WebPushCrypto.encodePoint((ECPublicKey) p256().getPublic())));
        subscription.setAuth(B64.encodeToString(new byte[16]));

        int status = sender.send(subscription, "{\"title\":\"Hi\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(status).isEqualTo(201);
        assertThat(headers.get().getFirst("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(headers.get().getFirst("TTL")).isEqualTo("86400");
        String authorization = headers.get().getFirst("Authorization");
        assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + properties.getPublicKey());
        String jwt = authorization.substring("vapid t=".length(), authorization.indexOf(','));
        Claims claims = Jwts.parser().verifyWith(vapid.getPublic()).build().parseSignedClaims(jwt).getPayload();
        assertThat(claims.getAudience()).containsExactly(origin);
        assertThat(claims.getSubject()).isEqualTo("mailto:ops@example.com");
        // salt (16) · record size 4096 · key length 65 · our key · ciphertext with its 16-byte tag
        ByteBuffer framing = ByteBuffer.wrap(body.get(), 16, 5);
        assertThat(framing.getInt()).isEqualTo(4096);
        assertThat(framing.get()).isEqualTo((byte) 65);
        assertThat(body.get()).hasSize(16 + 5 + 65 + "{\"title\":\"Hi\"}".length() + 1 + 16);
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(WebPushCrypto.P256);
        return generator.generateKeyPair();
    }

    private static byte[] unsigned32(byte[] bytes) {
        byte[] out = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }
}
