package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.security.SecurityProperties;
import com.nayeem.habittracker.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens (plan §4.1): opaque random values, stored only as a SHA-256 hash, rotated on
 * every use. Presenting a token that was already rotated or logged out means it was copied, so
 * every token of that user is revoked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;

    /** Stores a new token for the user and returns its raw value — the only time it exists. */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(properties.getRefreshToken().getTtl()));
        repository.save(token);
        return raw;
    }

    /**
     * Revokes the presented token and returns its user's id; the caller issues the new pair.
     * {@code noRollbackFor}: the reuse branch revokes everything and then throws — that revoke
     * must stick.
     */
    @Transactional(noRollbackFor = ApplicationException.class)
    public Long consume(String raw) {
        RefreshToken token = repository.findByTokenHashForUpdate(hash(raw))
                .orElseThrow(RefreshTokenService::invalid);
        Long userId = token.getUser().getId();

        if (token.isRevoked()) {
            int revoked = repository.revokeAllForUser(userId);
            log.warn("Refresh token reuse for user {} - revoked {} live token(s)", userId, revoked);
            throw invalid();
        }
        if (!token.isUsableAt(Instant.now())) {
            throw invalid();
        }
        token.setRevoked(true);
        return userId;
    }

    /** Logout. Unknown or already revoked tokens are ignored, so logout is idempotent. */
    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(hash(raw)).ifPresent(token -> token.setRevoked(true));
    }

    /** Signs the user out everywhere (password reset or change). */
    @Transactional
    public int revokeAll(Long userId) {
        return repository.revokeAllForUser(userId);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
    }
}
