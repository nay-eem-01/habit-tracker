package com.nayeem.habittracker.habit;

import java.time.LocalDate;

/**
 * Whether a habit is due on a day — for reminders (plan §12.2) and the dashboard's today (§11.4).
 * Pure, so it is unit-tested alone.
 */
public final class DueRules {

    private DueRules() {
    }

    /**
     * {@code DAILY}: always. {@code SPECIFIC_DAYS}: on its weekdays. {@code X_TIMES_PER_WEEK}: until
     * the week's quota (Monday–Sunday) is met.
     *
     * @param doneDaysThisWeek days this week, before today, the habit reached its target; only used
     *                         for N-a-week
     */
    public static boolean isDue(FrequencyType type, FrequencyConfig config, LocalDate today, long doneDaysThisWeek) {
        return switch (type) {
            case DAILY -> true;
            case SPECIFIC_DAYS -> config.days().contains(today.getDayOfWeek());
            case X_TIMES_PER_WEEK -> doneDaysThisWeek < config.timesPerWeek();
        };
    }
}
