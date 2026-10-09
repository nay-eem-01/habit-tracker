package com.nayeem.habittracker.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counters in memory, per key ("login-ip:1.2.3.4", "login-fail:a@b.c").
 * shortcut: one instance only — with several app instances each counts on its own; move the counters
 * to Redis or the database then.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final int CLEAN_UP_ABOVE = 10_000;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Counts one more hit; false (and not counted) when the key already had {@code limit} in this window. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            Window current = w == null || w.isOver(clock.instant()) ? new Window(clock.instant().plus(window), 0) : w;
            allowed[0] = current.count < limit;
            return allowed[0] ? new Window(current.endsAt, current.count + 1) : current;
        });
        cleanUpIfLarge();
        return allowed[0];
    }

    /** True when the key has used up {@code limit} in its current window; counts nothing. */
    public boolean isExhausted(String key, int limit) {
        Window w = windows.get(key);
        return w != null && !w.isOver(clock.instant()) && w.count >= limit;
    }

    /** Seconds until the key's window ends (for {@code Retry-After}); 0 when there is none. */
    public long secondsLeft(String key) {
        Window w = windows.get(key);
        return w == null ? 0 : Math.max(0, Duration.between(clock.instant(), w.endsAt).toSeconds() + 1);
    }

    private void cleanUpIfLarge() {
        if (windows.size() > CLEAN_UP_ABOVE) {
            Instant now = clock.instant();
            windows.values().removeIf(w -> w.isOver(now));
        }
    }

    private record Window(Instant endsAt, int count) {
        boolean isOver(Instant now) {
            return !now.isBefore(endsAt);
        }
    }
}
