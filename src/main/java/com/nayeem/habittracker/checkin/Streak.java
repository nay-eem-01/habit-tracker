package com.nayeem.habittracker.checkin;

/** A habit's streak: the run it's on now, and the best run it ever had, in {@code unit}s. */
public record Streak(int current, int longest, StreakUnit unit) {
}
