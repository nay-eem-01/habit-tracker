package com.nayeem.habittracker.goal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** One linked habit's share of its goal. */
public record HabitGoalProgress(
        Long habitId,
        String name,
        boolean archived,
        @Schema(description = "The owner's day the habit was linked; done days count from then on")
        LocalDate linkedOn,
        @Schema(description = "Days since linking on which the habit reached its daily target")
        long doneDays,
        int goalTargetDays,
        @Schema(description = "0-100, whole percent; capped at 100")
        int percent) {
}
