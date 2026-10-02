package com.nayeem.habittracker.goal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record GoalProgress(
        Long goalId,
        @Schema(description = "0-100, whole percent: the average over the habits that are not archived; 0 with none")
        int percent,
        List<HabitGoalProgress> habits) {
}
