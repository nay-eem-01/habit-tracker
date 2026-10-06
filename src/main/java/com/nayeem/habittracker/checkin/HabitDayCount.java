package com.nayeem.habittracker.checkin;

/** One habit's check-in count on one day — a row of {@link HabitLogRepository#findCountsOn}. */
record HabitDayCount(Long habitId, int completedCount) {
}
