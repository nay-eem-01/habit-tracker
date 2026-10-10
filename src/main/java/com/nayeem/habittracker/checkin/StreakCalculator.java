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
 *
 * <p>{@link #walk} exposes the run unit by unit, so other read-side numbers (XP, PLAN.md §11.3)
 * follow exactly the same rules instead of a copy of them.
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    /** Sees every unit (scheduled day, or week) from the habit's first to today, in order. */
    @FunctionalInterface
    public interface UnitVisitor {
        /**
         * @param countedDays done days in this unit that count: 0 or 1 for a day; for a week, the
         *                    done days up to N (extra days don't count twice)
         * @param run         the streak right after this unit
         */
        void visit(int countedDays, int run);
    }

    public static StreakUnit unitOf(FrequencyType type) {
        return type == FrequencyType.X_TIMES_PER_WEEK ? StreakUnit.WEEKS : StreakUnit.DAYS;
    }

    /**
     * @param doneDays days where {@code completedCount >= targetCount}
     * @param start    the habit's first day, in the user's timezone
     * @param today    the user's today
     */
    public static Streak calculate(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                                   LocalDate start, LocalDate today) {
        return calculate(type, config, doneDays, Set.of(), start, today);
    }

    /** @param restDays days the user rested: skipped like an unscheduled day (PLAN.md §3.4) */
    public static Streak calculate(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                                   Set<LocalDate> restDays, LocalDate start, LocalDate today) {
        int[] current = {0};
        int[] longest = {0};
        walk(type, config, doneDays, restDays, start, today, (countedDays, run) -> {
            current[0] = run;
            longest[0] = Math.max(longest[0], run);
        });
        return new Streak(current[0], longest[0], unitOf(type));
    }

    /** Walks the habit's units from {@code start} to {@code today} with the same rules as {@link #calculate}. */
    public static void walk(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                            Set<LocalDate> restDays, LocalDate start, LocalDate today, UnitVisitor visitor) {
        switch (type) {
            case DAILY -> walkDays(doneDays, restDays, start, today, null, visitor);
            case SPECIFIC_DAYS -> walkDays(doneDays, restDays, start, today, config.days(), visitor);
            case X_TIMES_PER_WEEK -> walkWeeks(doneDays, start, today, config.timesPerWeek(), visitor);
        }
    }

    /** @param days the scheduled weekdays, or {@code null} for every day */
    private static void walkDays(Set<LocalDate> doneDays, Set<LocalDate> restDays, LocalDate start, LocalDate today,
                                 Set<DayOfWeek> days, UnitVisitor visitor) {
        int run = 0;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if ((days != null && !days.contains(day.getDayOfWeek())) || restDays.contains(day)) {
                continue;
            }
            boolean done = doneDays.contains(day);
            if (done) {
                run++;
            } else if (!day.equals(today)) {
                run = 0;
            }
            visitor.visit(done ? 1 : 0, run);
        }
    }

    private static void walkWeeks(Set<LocalDate> doneDays, LocalDate start, LocalDate today, int timesPerWeek,
                                  UnitVisitor visitor) {
        Map<LocalDate, Integer> doneByWeek = new HashMap<>();
        for (LocalDate day : doneDays) {
            if (!day.isBefore(start) && !day.isAfter(today)) {
                doneByWeek.merge(weekOf(day), 1, Integer::sum);
            }
        }

        LocalDate firstWeek = weekOf(start);
        LocalDate thisWeek = weekOf(today);
        int run = 0;
        for (LocalDate week = firstWeek; !week.isAfter(thisWeek); week = week.plusWeeks(1)) {
            int done = doneByWeek.getOrDefault(week, 0);
            if (done >= timesPerWeek) {
                run++;
            } else if (!week.equals(thisWeek) && !week.equals(firstWeek)) {
                run = 0;
            }
            visitor.visit(Math.min(done, timesPerWeek), run);
        }
    }

    static LocalDate weekOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
