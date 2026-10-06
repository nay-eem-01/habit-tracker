package com.nayeem.habittracker.level;

/**
 * Where a total XP puts a user (PLAN.md §11.3). Level {@code n} starts at {@code 50·(n−1)·n} XP:
 * level 2 at 100, 3 at 300, 4 at 600, 10 at 4 500. Early levels come fast; later ones take weeks.
 *
 * @param xpForNextLevel      total XP at which the next level starts
 * @param progressToNextLevel 0–1, two decimals: how far through the current level
 */
public record Level(long xp, int level, Tier tier, long xpForNextLevel, double progressToNextLevel) {

    public static Level of(long xp) {
        if (xp < 0) {
            throw new IllegalArgumentException("XP can't be negative");
        }
        int level = levelFor(xp);
        long from = startOf(level);
        long next = startOf(level + 1);
        double progress = Math.floor((double) (xp - from) / (next - from) * 100) / 100;
        return new Level(xp, level, Tier.of(level), next, progress);
    }

    /** Total XP at which {@code level} starts. */
    static long startOf(int level) {
        return 50L * (level - 1) * level;
    }

    private static int levelFor(long xp) {
        // largest n with 50·(n−1)·n ≤ xp; the square root gets close, the loops fix rounding
        int n = (int) ((1 + Math.sqrt(1 + xp / 12.5)) / 2);
        while (startOf(n + 1) <= xp) {
            n++;
        }
        while (n > 1 && startOf(n) > xp) {
            n--;
        }
        return Math.max(n, 1);
    }
}
