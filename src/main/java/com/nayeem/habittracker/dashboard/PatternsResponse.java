package com.nayeem.habittracker.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** When and how consistently the user does their habits (PLAN.md §11.4, roadmap A.2). */
public record PatternsResponse(
        @Schema(description = "The last 365 days, oldest first, today included") List<HeatmapDay> heatmap,
        @Schema(description = "Monday to Sunday over the last 12 full weeks") List<WeekdayRate> weekdays,
        @Schema(description = "Lowest weekday rate; null without data. Ties: the earlier weekday") DayOfWeek weakestDay,
        @Schema(description = "Highest weekday rate; null without data. Ties: the earlier weekday") DayOfWeek strongestDay,
        @Schema(description = "Done check-ins of the last 90 days by hour of the first check-in (0–23), "
                + "made on the day they were for") List<Integer> hours,
        @Schema(description = "The busiest hour; null without check-ins. Ties: the earlier hour") Integer peakHour) {

    public record HeatmapDay(
            LocalDate date,
            @Schema(description = "Habits done that day (of those expected)") int done,
            @Schema(description = "Daily and chosen-weekday habits scheduled that day, plus N-a-week habits done "
                    + "that day — an N-a-week habit never counts against a day") int expected,
            @Schema(description = "done / expected, 2 decimals; null when nothing was expected") Double ratio) {
    }

    public record WeekdayRate(
            DayOfWeek day,
            int done,
            int expected,
            @Schema(description = "Daily and chosen-weekday habits only; null when nothing was expected") Double rate) {
    }
}
