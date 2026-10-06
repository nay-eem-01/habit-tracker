package com.nayeem.habittracker.level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LevelTest {

    @ParameterizedTest
    @CsvSource({
            "0, 1, BRONZE",
            "99, 1, BRONZE",
            "100, 2, BRONZE",
            "299, 2, BRONZE",
            "300, 3, BRONZE",
            "600, 4, BRONZE",
            "999, 4, BRONZE",
            "1000, 5, SILVER",
            "4499, 9, SILVER",
            "4500, 10, GOLD",
            "18999, 19, GOLD",
            "19000, 20, PLATINUM",
            "59499, 34, PLATINUM",
            "59500, 35, DIAMOND"})
    void levelAndTierFromTotalXp(long xp, int level, Tier tier) {
        Level result = Level.of(xp);

        assertThat(result.level()).isEqualTo(level);
        assertThat(result.tier()).isEqualTo(tier);
    }

    @Test
    void progressThroughTheCurrentLevel() {
        Level level = Level.of(200);   // level 2 runs from 100 to 300

        assertThat(level.xpForNextLevel()).isEqualTo(300);
        assertThat(level.progressToNextLevel()).isEqualTo(0.5);
        assertThat(Level.of(0).progressToNextLevel()).isZero();
        assertThat(Level.of(299).progressToNextLevel()).isEqualTo(0.99);   // never shows 1.0 before levelling up
    }

    @Test
    void staysExactForLargeTotals() {
        for (long xp = 1_000_000; xp < 50_000_000; xp += 999_983) {
            Level level = Level.of(xp);
            assertThat(Level.startOf(level.level())).isLessThanOrEqualTo(xp);
            assertThat(Level.startOf(level.level() + 1)).isGreaterThan(xp);
        }
    }

    @Test
    void negativeXpIsAProgrammingError() {
        assertThatThrownBy(() -> Level.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
