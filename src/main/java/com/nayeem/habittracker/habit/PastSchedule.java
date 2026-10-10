package com.nayeem.habittracker.habit;

import java.time.LocalDate;

/** A schedule a habit had before it was changed, and the owner's days it applied ({@code from}–{@code until}). */
public record PastSchedule(FrequencyType type, FrequencyConfig config, LocalDate from, LocalDate until) {
}
