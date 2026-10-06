package com.nayeem.habittracker.checkin;

import java.time.Instant;
import java.time.LocalDate;

/** A done day and when it was first logged (the log row's {@code createdAt}). */
public record CheckInTime(Long habitId, LocalDate day, Instant firstLoggedAt) {
}
