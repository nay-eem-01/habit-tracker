package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;

import java.time.LocalDate;
import java.util.Set;

/**
 * Completion rate over a window ending today, or the one before it (PLAN.md §12.1, §11.4). Pure, like {@link StreakCalculator}.
 *
 * <ul>
 *   <li>The window is clipped to the habit's first day, so a new habit isn't judged on days
 *       before it existed.</li>
 *   <li>Today is counted only once it's done — an evening check-in shouldn't make the morning
 *       look like a failure.</li>
 *   <li>{@code DAILY} / {@code SPECIFIC_DAYS}: done scheduled days ÷ scheduled days. Check-ins on
 *       unscheduled days stay in the logs but count for neither the rate nor the streak.</li>
 *   <li>{@code X_TIMES_PER_WEEK}: done days ÷ ({@code timesPerWeek} × days / 7), capped at 1.</li>
 * </ul>
 */
public final class StatsCalculator {

    private StatsCalculator() {
    }

    public static WindowStats window(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                                     LocalDate start, LocalDate today, int days) {
        return window(type, config, doneDays, Set.of(), start, today, days);
    }

    /** @param restDays days the user rested: not expected (PLAN.md §3.4) */
    public static WindowStats window(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                                     Set<LocalDate> restDays, LocalDate start, LocalDate today, int days) {
        LocalDate to = doneDays.contains(today) ? today : today.minusDays(1);
        return range(type, config, doneDays, restDays, start, today.minusDays(days - 1L), to, days);
    }

    /**
     * The {@code days} days just before {@link #window}'s — for "change against the previous
     * period" (PLAN.md §11.4). Already over, so every day counts, done or not.
     */
    public static WindowStats previousWindow(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                                             LocalDate start, LocalDate today, int days) {
        return previousWindow(type, config, doneDays, Set.of(), start, today, days);
    }

    public static WindowStats previousWindow(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                                             Set<LocalDate> restDays, LocalDate start, LocalDate today, int days) {
        LocalDate to = today.minusDays(days);
        return range(type, config, doneDays, restDays, start, to.minusDays(days - 1L), to, days);
    }

    /** {@code from..to} inclusive, clipped to the habit's first day; empty when it ends first. */
    private static WindowStats range(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                                     Set<LocalDate> restDays, LocalDate start, LocalDate windowStart, LocalDate to,
                                     int days) {
        LocalDate from = windowStart.isBefore(start) ? start : windowStart;

        int done = 0;
        double expected = 0;
        if (type == FrequencyType.X_TIMES_PER_WEEK) {
            int span = 0;
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                span++;
                if (doneDays.contains(day)) {
                    done++;
                }
            }
            expected = config.timesPerWeek() * span / 7.0;
        } else {
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                boolean scheduled = (type == FrequencyType.DAILY || config.days().contains(day.getDayOfWeek()))
                        && !restDays.contains(day);
                if (scheduled) {
                    expected++;
                    if (doneDays.contains(day)) {
                        done++;
                    }
                }
            }
        }

        Double rate = expected == 0 ? null : round2(Math.min(done / expected, 1.0));
        return new WindowStats(days, done, round2(expected), rate);
    }

    static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
