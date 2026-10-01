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
        @JsonFormat(pattern = "HH:mm") LocalTime reminderTime,
        boolean archived,
        Instant createdAt) {

    static HabitResponse from(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getCategory(), habit.getFrequencyType(),
                habit.getFrequencyConfig(), habit.getTargetCount(), habit.getReminderTime(),
                habit.isArchived(), habit.getCreatedAt());
    }
}
