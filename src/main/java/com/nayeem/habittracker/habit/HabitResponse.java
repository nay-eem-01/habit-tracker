package com.nayeem.habittracker.habit;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalTime;

public record HabitResponse(
        Long id,
        String name,
        String category,
        FrequencyType frequencyType,
        FrequencyConfig frequencyConfig,
        int targetCount,
        String unit,
        @JsonFormat(pattern = "HH:mm") LocalTime reminderTime,
        boolean archived,
        Long goalId,
        Integer goalTargetDays,
        Instant createdAt) {

    static HabitResponse from(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getCategory(), habit.getFrequencyType(),
                habit.getFrequencyConfig(), habit.getTargetCount(), habit.getUnit(), habit.getReminderTime(),
                habit.isArchived(), habit.getGoal() == null ? null : habit.getGoal().getId(),
                habit.getGoalTargetDays(), habit.getCreatedAt());
    }
}
