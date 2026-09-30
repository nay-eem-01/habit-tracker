package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Strict streaks, computed from done days on every read — never stored (plan §1.3–1.4, PLAN.md
 * §8b and §12.1). Pure: no Spring, no database, "today" is passed in.
 *
 * <ul>
 *   <li>{@code DAILY} / {@code SPECIFIC_DAYS}: counts scheduled days. A scheduled day that ended
 *       undone resets the run; unscheduled days are skipped; today doesn't break it while it's
 *       still going.</li>
 *   <li>{@code X_TIMES_PER_WEEK}: counts Monday–Sunday weeks with at least N done days. The
 *       current week doesn't break it while it's going, and neither does the habit's first
 *       (possibly partial) week.</li>
 * </ul>
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    /**
     * @param doneDays days where {@code completedCount >= targetCount}
     * @param start    the habit's first day, in the user's timezone
     * @param today    the user's today
     */
    public static Streak calculate(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                                   LocalDate start, LocalDate today) {
        return switch (type) {
            case DAILY -> dayStreak(doneDays, start, today, null);
            case SPECIFIC_DAYS -> dayStreak(doneDays, start, today, config.days());
            case X_TIMES_PER_WEEK -> weekStreak(doneDays, start, today, config.timesPerWeek());
        };
    }

    /** @param days the scheduled weekdays, or {@code null} for every day */
    private static Streak dayStreak(Set<LocalDate> doneDays, LocalDate start, LocalDate today, Set<DayOfWeek> days) {
        int run = 0;
        int longest = 0;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if (days != null && !days.contains(day.getDayOfWeek())) {
                continue;
            }
            if (doneDays.contains(day)) {
                longest = Math.max(longest, ++run);
            } else if (!day.equals(today)) {
                run = 0;
            }
        }
        return new Streak(run, longest, StreakUnit.DAYS);
    }

    private static Streak weekStreak(Set<LocalDate> doneDays, LocalDate start, LocalDate today, int timesPerWeek) {
        Map<LocalDate, Integer> doneByWeek = new HashMap<>();
        for (LocalDate day : doneDays) {
            if (!day.isBefore(start) && !day.isAfter(today)) {
                doneByWeek.merge(weekOf(day), 1, Integer::sum);
            }
        }

        LocalDate firstWeek = weekOf(start);
        LocalDate thisWeek = weekOf(today);
        int run = 0;
        int longest = 0;
        for (LocalDate week = firstWeek; !week.isAfter(thisWeek); week = week.plusWeeks(1)) {
            if (doneByWeek.getOrDefault(week, 0) >= timesPerWeek) {
                longest = Math.max(longest, ++run);
            } else if (!week.equals(thisWeek) && !week.equals(firstWeek)) {
                run = 0;
            }
        }
        return new Streak(run, longest, StreakUnit.WEEKS);
    }

    static LocalDate weekOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
