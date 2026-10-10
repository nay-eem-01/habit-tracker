package com.nayeem.habittracker.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** {@code app.push.*} — the VAPID key pair identifies this server to the browsers' push services. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.push")
public class PushProperties {

    /** Off: subscriptions are kept, nothing is sent (only logged). */
    private boolean enabled;
    /** Base64url, uncompressed P-256 point; the web app subscribes with it. */
    private String publicKey;
    /** Base64url, the 32-byte private scalar. Secret: from the environment only. */
    private String privateKey;
    /** Who runs this server, for the push services: {@code mailto:} or an https URL. */
    private String subject;
}
