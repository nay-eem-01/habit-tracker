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
class RefreshTokenRepositoryIntegrationTest extends IntegrationTest {

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private UserService userService;

    @Test
    void findsByHashAndRevokesAllOfOneUser() {
        User alice = userService.createLocalUser("alice@example.com", "hash", "Alice", null);
        User bob = userService.createLocalUser("bob@example.com", "hash", "Bob", null);
        save(alice, "a".repeat(64));
        save(alice, "b".repeat(64));
        save(bob, "c".repeat(64));

        assertThat(repository.findByTokenHash("a".repeat(64))).isPresent();

        assertThat(repository.revokeAllForUser(alice.getId())).isEqualTo(2);
        assertThat(repository.findByTokenHash("b".repeat(64)).orElseThrow().isRevoked()).isTrue();
        assertThat(repository.findByTokenHash("c".repeat(64)).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void usableOnlyWhenNotRevokedAndNotExpired() {
        Instant now = Instant.now();
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(now.plus(1, ChronoUnit.MINUTES));

        assertThat(token.isUsableAt(now)).isTrue();
        assertThat(token.isUsableAt(now.plus(2, ChronoUnit.MINUTES))).isFalse();
        token.setRevoked(true);
        assertThat(token.isUsableAt(now)).isFalse();
    }

    private void save(User user, String hash) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash);
        token.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        repository.save(token);
    }
}
