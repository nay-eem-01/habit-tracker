package com.nayeem.habittracker.habit;

import java.time.Instant;

public record HabitResponse(
        Long id,
        String name,
        String category,
        FrequencyType frequencyType,
        FrequencyConfig frequencyConfig,
        int targetCount,
        boolean archived,
        Instant createdAt) {

    static HabitResponse from(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getCategory(), habit.getFrequencyType(),
                habit.getFrequencyConfig(), habit.getTargetCount(), habit.isArchived(), habit.getCreatedAt());
    }
}
