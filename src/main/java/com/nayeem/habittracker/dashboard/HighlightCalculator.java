package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.dashboard.DashboardResponse.AtRiskHabit;
import com.nayeem.habittracker.dashboard.DashboardResponse.HabitCompletion;
import com.nayeem.habittracker.dashboard.DashboardResponse.HabitRate;
import com.nayeem.habittracker.dashboard.DashboardResponse.TodayHabit;
import com.nayeem.habittracker.habit.FrequencyType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Streaks at risk, best and slipping habits (PLAN.md §11.4). Pure, from numbers already computed. */
final class HighlightCalculator {

    /** A shorter streak isn't worth a warning. */
    static final int AT_RISK_FROM = 3;
    static final int TOP = 3;

    private HighlightCalculator() {
    }

    /**
     * Habits whose streak (≥ {@value #AT_RISK_FROM}) ends unless something happens today: a daily or
     * chosen-weekday habit due and not done; an N-a-week habit that needs as many more days as the week
     * has left.
     */
    static List<AtRiskHabit> atRisk(List<TodayHabit> habits, LocalDate today) {
        int daysLeftAfterToday = DayOfWeek.SUNDAY.getValue() - today.getDayOfWeek().getValue();
        return habits.stream()
                .filter(h -> h.streak() >= AT_RISK_FROM)
                .map(h -> {
                    if (h.frequencyType() != FrequencyType.X_TIMES_PER_WEEK) {
                        return h.due() && !h.done() ? new AtRiskHabit(h.habitId(), h.name(), h.streak(), h.streakUnit(), 1) : null;
                    }
                    int needed = h.timesPerWeek() - h.doneThisWeek();
                    int daysAvailable = daysLeftAfterToday + (h.done() ? 0 : 1);
                    return needed > 0 && needed >= daysAvailable
                            ? new AtRiskHabit(h.habitId(), h.name(), h.streak(), h.streakUnit(), needed) : null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(AtRiskHabit::streak).reversed())
                .toList();
    }

    /** Up to {@value #TOP} highest 30-day rates. */
    static List<HabitRate> best(List<HabitCompletion> habits) {
        return habits.stream()
                .filter(h -> h.last30Days().rate() != null)
                .sorted(Comparator.comparingDouble((HabitCompletion h) -> h.last30Days().rate()).reversed())
                .limit(TOP)
                .map(HighlightCalculator::rate)
                .toList();
    }

    /** Up to {@value #TOP} biggest drops in the 30-day rate against the 30 days before; only real drops. */
    static List<HabitRate> slipping(List<HabitCompletion> habits) {
        return habits.stream()
                .filter(h -> h.last30Days().change() != null && h.last30Days().change() < 0)
                .sorted(Comparator.comparingDouble(h -> h.last30Days().change()))
                .limit(TOP)
                .map(HighlightCalculator::rate)
                .toList();
    }

    private static HabitRate rate(HabitCompletion habit) {
        return new HabitRate(habit.habitId(), habit.name(), habit.last30Days().rate(), habit.last30Days().change());
    }
}
