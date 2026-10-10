package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.support.IntegrationTest;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ExpiredTokenCleanupIntegrationTest extends IntegrationTest {

    @Autowired
    private ExpiredTokenCleanup cleanup;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Autowired
    private UserService userService;

    @Test
    void deletesTokensExpiredMoreThanADayAgoOnly() {
        User user = userService.createLocalUser("cleanup@example.com", "hash", "Test", null);
        Instant now = Instant.now();
        RefreshToken old = refreshToken(user, "a", now.minus(2, ChronoUnit.DAYS));
        RefreshToken recent = refreshToken(user, "b", now.minus(1, ChronoUnit.HOURS));
        RefreshToken live = refreshToken(user, "c", now.plus(7, ChronoUnit.DAYS));
        OneTimeToken oldLink = resetLink(user, "d", now.minus(2, ChronoUnit.DAYS));
        OneTimeToken liveLink = resetLink(user, "e", now.plus(30, ChronoUnit.MINUTES));

        cleanup.run();

        assertThat(refreshTokenRepository.findById(old.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(recent.getId())).isPresent();
        assertThat(refreshTokenRepository.findById(live.getId())).isPresent();
        assertThat(oneTimeTokenRepository.findById(oldLink.getId())).isEmpty();
        assertThat(oneTimeTokenRepository.findById(liveLink.getId())).isPresent();
    }

    private RefreshToken refreshToken(User user, String c, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(c.repeat(64));
        token.setExpiresAt(expiresAt);
        return refreshTokenRepository.save(token);
    }

    private OneTimeToken resetLink(User user, String c, Instant expiresAt) {
        OneTimeToken token = new OneTimeToken();
        token.setUser(user);
        token.setPurpose(TokenPurpose.PASSWORD_RESET);
        token.setTokenHash(c.repeat(64));
        token.setExpiresAt(expiresAt);
        return oneTimeTokenRepository.save(token);
    }
}
