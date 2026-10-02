package com.nayeem.habittracker.goal;

import java.time.Instant;
import java.time.LocalDate;

public record GoalResponse(
        Long id,
        String title,
        String description,
        LocalDate targetDate,
        GoalStatus status,
        Instant achievedAt,
        Instant createdAt) {

    static GoalResponse from(Goal goal) {
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getTargetDate(),
                goal.getStatus(), goal.getAchievedAt(), goal.getCreatedAt());
    }
}
