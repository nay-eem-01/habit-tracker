package com.nayeem.habittracker.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsTheLimitPerWindowThenRefusesUntilItEnds() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.isExhausted("k", 3)).isTrue();
        assertThat(limiter.secondsLeft("k")).isBetween(1L, 61L);
        assertThat(limiter.tryAcquire("other", 3, Duration.ofMinutes(1))).isTrue();

        clock.now = clock.now.plusSeconds(60);
        assertThat(limiter.isExhausted("k", 3)).isFalse();
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
