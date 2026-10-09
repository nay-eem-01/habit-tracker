package com.nayeem.habittracker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Every night, deletes refresh tokens and one-time links that expired more than a day ago — one row is
 * added on every sign-in and refresh, so without this the tables only grow. An expired token is
 * refused either way; deleting it changes no answer.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
class ExpiredTokenCleanup {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final Clock clock;

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void run() {
        Instant before = clock.instant().minus(Duration.ofDays(1));
        int refresh = refreshTokenRepository.deleteExpiredBefore(before);
        int links = oneTimeTokenRepository.deleteExpiredBefore(before);
        log.info("Deleted {} expired refresh token(s) and {} expired one-time link(s)", refresh, links);
    }
}
