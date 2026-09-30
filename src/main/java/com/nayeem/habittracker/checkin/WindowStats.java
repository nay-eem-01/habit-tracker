package com.nayeem.habittracker.checkin;

import io.swagger.v3.oas.annotations.media.Schema;

/** Completion over the last {@code days} days. */
public record WindowStats(
        int days,
        @Schema(description = "Done days that counted") int done,
        @Schema(description = "Done days the schedule asked for in the window") double expected,
        @Schema(description = "done / expected, 0-1 with 2 decimals, capped at 1; null when nothing was expected yet")
        Double rate) {
}
