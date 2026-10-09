package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Emailed one-time links (reset, verification): 32 random bytes, stored as SHA-256 only, single use,
 * and limited per user to one a minute and {@code maxPerHour} an hour.
 */
@Service
@RequiredArgsConstructor
class OneTimeTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OneTimeTokenRepository repository;
    private final Clock clock;

    /** A new token's raw value — the only time it exists — or empty when the user asked too often. */
    @Transactional
    public Optional<String> issue(User user, TokenPurpose purpose, Duration ttl, int maxPerHour) {
        Instant now = clock.instant();
        if (issuedSince(user, purpose, now.minus(Duration.ofMinutes(1))) > 0
                || issuedSince(user, purpose, now.minus(Duration.ofHours(1))) >= maxPerHour) {
            return Optional.empty();
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OneTimeToken token = new OneTimeToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTokenHash(RefreshTokenService.hash(raw));
        token.setExpiresAt(now.plus(ttl));
        repository.save(token);
        return Optional.of(raw);
    }

    /** Uses a token: its user, with every other token of theirs for that purpose retired; empty if it doesn't work. */
    @Transactional
    public Optional<User> consume(String raw, TokenPurpose purpose) {
        Instant now = clock.instant();
        return repository.findByTokenHashForUpdate(RefreshTokenService.hash(raw))
                .filter(t -> t.getPurpose() == purpose && t.isUsableAt(now))
                .map(t -> {
                    repository.retireAll(t.getUser().getId(), purpose, now);
                    return t.getUser();
                });
    }

    private long issuedSince(User user, TokenPurpose purpose, Instant since) {
        return repository.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, since);
    }

    @Transactional
    public void retireAll(Long userId, TokenPurpose purpose) {
        repository.retireAll(userId, purpose, clock.instant());
    }
}
