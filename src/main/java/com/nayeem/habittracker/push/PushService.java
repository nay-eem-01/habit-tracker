package com.nayeem.habittracker.push;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Web push subscriptions and sending (PLAN.md §3.6). The server POSTs to a subscription's endpoint,
 * so only https URLs of the browsers' own push services are accepted — anything else would let a
 * user point the server at any address. Logs ids only: endpoints are capability URLs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    /** Chrome/Edge (FCM), Firefox, Safari, Windows. */
    private static final List<String> PUSH_SERVICE_HOSTS = List.of(
            "fcm.googleapis.com", ".push.services.mozilla.com", ".push.apple.com", ".notify.windows.com");
    private static final Set<Integer> GONE = Set.of(404, 410);

    private final PushSubscriptionRepository repository;
    private final UserService userService;
    private final WebPushSender sender;
    private final PushProperties properties;
    private final ObjectMapper objectMapper;

    public PushKeyResponse publicKey() {
        return new PushKeyResponse(properties.isEnabled(), properties.isEnabled() ? properties.getPublicKey() : null);
    }

    /** Saves the browser's subscription; the same browser signing in as someone else moves it to them. */
    @Transactional
    public void subscribe(Long userId, SubscribeRequest request) {
        checkEndpoint(request.getEndpoint());
        checkKey(request.getKeys().getP256dh(), 65);
        checkKey(request.getKeys().getAuth(), 16);
        PushSubscription subscription = repository.findByEndpoint(request.getEndpoint()).orElseGet(PushSubscription::new);
        subscription.setUser(userService.getById(userId));
        subscription.setEndpoint(request.getEndpoint());
        subscription.setP256dh(request.getKeys().getP256dh());
        subscription.setAuth(request.getKeys().getAuth());
        subscription = repository.save(subscription);
        log.info("Push subscription {} saved for user {}", subscription.getId(), userId);
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        repository.deleteByEndpointAndUserId(endpoint, userId);
    }

    /**
     * Sends to every browser of the user; one the push service says is gone is deleted. Failures are
     * logged, never thrown: a missed push mustn't fail what triggered it.
     */
    @Transactional
    public void sendToUser(Long userId, String title, String body, String url) {
        List<PushSubscription> subscriptions = repository.findAllByUserId(userId);
        if (!properties.isEnabled()) {
            log.info("Push disabled; would have sent to {} browser(s) of user {}", subscriptions.size(), userId);
            return;
        }
        byte[] payload = objectMapper.writeValueAsBytes(Map.of("title", title, "body", body, "url", url));
        for (PushSubscription subscription : subscriptions) {
            try {
                int status = sender.send(subscription, payload);
                if (GONE.contains(status)) {
                    repository.delete(subscription);
                    log.info("Push subscription {} of user {} is gone; deleted", subscription.getId(), userId);
                } else if (status >= 400) {
                    log.warn("Push to subscription {} of user {} refused: {}", subscription.getId(), userId, status);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Push to subscription {} of user {} failed: {}", subscription.getId(), userId,
                        e.getClass().getSimpleName());
            }
        }
    }

    static void checkEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || PUSH_SERVICE_HOSTS.stream().noneMatch(h -> h.startsWith(".") ? host.endsWith(h) : host.equals(h))) {
            throw invalid();
        }
    }

    private static void checkKey(String base64url, int length) {
        try {
            if (Base64.getUrlDecoder().decode(base64url).length != length) {
                throw invalid();
            }
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.PUSH_SUBSCRIPTION_INVALID);
    }
}
