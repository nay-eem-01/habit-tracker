package com.nayeem.habittracker.checkin;

import java.time.LocalDate;

public record HabitLogResponse(Long id, LocalDate date, int completedCount, boolean done, String note) {

    static HabitLogResponse from(HabitLog log) {
        return new HabitLogResponse(log.getId(), log.getLogDate(), log.getCompletedCount(), log.isDone(), log.getNote());
    }
}
