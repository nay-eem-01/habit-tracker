package com.nayeem.habittracker.checkin;

import java.time.LocalDate;

/** One logged day in the data export; {@code targetCount} is the target the day was judged by. */
public record ExportedCheckIn(Long habitId, LocalDate date, int completedCount, int targetCount, String note) {
}
