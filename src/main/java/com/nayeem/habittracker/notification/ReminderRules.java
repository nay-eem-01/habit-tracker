package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;

import java.time.LocalDate;

/** Whether a habit with a reminder is due today (plan §12.2). Pure, so it is unit-tested alone. */
final class ReminderRules {

    private ReminderRules() {
    }

    /**
     * {@code DAILY}: always. {@code SPECIFIC_DAYS}: on its weekdays. {@code X_TIMES_PER_WEEK}: until
     * the week's quota (Monday–Sunday) is met. The caller has already excluded a habit done today.
     *
     * @param doneDaysThisWeek days this week the habit reached its target; only used for N-a-week
     */
    static boolean isDue(FrequencyType type, FrequencyConfig config, LocalDate today, long doneDaysThisWeek) {
        return switch (type) {
            case DAILY -> true;
            case SPECIFIC_DAYS -> config.days().contains(today.getDayOfWeek());
            case X_TIMES_PER_WEEK -> doneDaysThisWeek < config.timesPerWeek();
        };
    }
}
