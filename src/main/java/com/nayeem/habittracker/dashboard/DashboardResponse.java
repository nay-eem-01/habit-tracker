package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.checkin.StreakUnit;
import com.nayeem.habittracker.habit.FrequencyType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** The home screen in one call (PLAN.md §11.4). Active habits only; everything in the user's timezone. */
public record DashboardResponse(Today today, Completion completion) {

    public record Today(
            @Schema(description = "The user's today") LocalDate date,
            @Schema(description = "Habits due today") int due,
            @Schema(description = "Of those, done") int done,
            @Schema(description = "Every active habit, due first, then the rest; by name") List<TodayHabit> habits) {
    }

    public record TodayHabit(
            Long habitId,
            String name,
            String category,
            FrequencyType frequencyType,
            int targetCount,
            @Schema(description = "Today's count so far") int completedCount,
            @Schema(description = "Today's count reached the target") boolean done,
            @Schema(description = "Daily: always. Chosen weekdays: on them. N-a-week: until this week's N is met "
                    + "(and on a day it was done)") boolean due,
            @Schema(description = "N-a-week only: done days this week (Mon–Sun), today included") Integer doneThisWeek,
            @Schema(description = "N-a-week only") Integer timesPerWeek,
            @Schema(description = "Current streak, in streakUnit") int streak,
            StreakUnit streakUnit) {
    }

    public record Completion(
            @Schema(description = "All active habits together") Period last7Days,
            Period last30Days,
            Period last90Days,
            List<HabitCompletion> habits) {
    }

    public record HabitCompletion(Long habitId, String name, Period last7Days, Period last30Days, Period last90Days) {
    }

    /** One window and the same-length window just before it. */
    public record Period(
            int days,
            @Schema(description = "Done days that counted") int done,
            @Schema(description = "Done days the schedules asked for") double expected,
            @Schema(description = "0–1, 2 decimals; null when nothing was expected yet") Double rate,
            @Schema(description = "The same over the previous `days` days; null when nothing was expected then")
            Double previousRate,
            @Schema(description = "rate − previousRate, 2 decimals; null when either is null", example = "0.12")
            Double change) {
    }
}
