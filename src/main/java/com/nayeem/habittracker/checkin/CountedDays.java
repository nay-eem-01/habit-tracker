package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.habit.HabitKind;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * The days a habit's streak, stats and XP count. For a build habit, its done days. For a quit habit
 * the logs are slips, so the clean days count — today too while it's still clean, and a slip today
 * breaks the run at once (PLAN.md §3.3).
 */
public final class CountedDays {

    private CountedDays() {
    }

    /** @param logged the habit's done days as logged: check-ins for a build habit, slips for a quit habit */
    public static Set<LocalDate> of(HabitKind kind, Set<LocalDate> logged, LocalDate start, LocalDate today) {
        if (kind != HabitKind.QUIT) {
            return logged;
        }
        Set<LocalDate> clean = new HashSet<>();
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if (!logged.contains(day)) {
                clean.add(day);
            }
        }
        return clean;
    }

    /**
     * The "today" to give {@link StreakCalculator}: for a quit habit tomorrow, so today is judged as a
     * finished day — counted when clean, breaking the run when slipped.
     */
    public static LocalDate streakToday(HabitKind kind, LocalDate today) {
        return kind == HabitKind.QUIT ? today.plusDays(1) : today;
    }
}
