package com.nayeem.habittracker.habit;

/** How often a habit is due (PLAN.md §8b). */
public enum FrequencyType {
    /** Every day. */
    DAILY,
    /** On the weekdays in {@link FrequencyConfig#days()}. */
    SPECIFIC_DAYS,
    /** {@link FrequencyConfig#timesPerWeek()} done days in each Monday–Sunday week, any days. */
    X_TIMES_PER_WEEK
}
