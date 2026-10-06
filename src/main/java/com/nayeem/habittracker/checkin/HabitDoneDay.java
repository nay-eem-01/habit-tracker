package com.nayeem.habittracker.checkin;

import java.time.LocalDate;

/** One done day of one habit — a row of {@link HabitLogRepository#findDoneDaysOfUser}. */
record HabitDoneDay(Long habitId, LocalDate day) {
}
