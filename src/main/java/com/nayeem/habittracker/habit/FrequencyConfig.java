package com.nayeem.habittracker.habit;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * The schedule details that depend on {@link FrequencyType}, stored as {@code jsonb}:
 * {@code {"days":["MONDAY","WEDNESDAY"]}} for {@code SPECIFIC_DAYS},
 * {@code {"timesPerWeek":3}} for {@code X_TIMES_PER_WEEK}, nothing for {@code DAILY}.
 */
@Schema(description = "SPECIFIC_DAYS needs days; X_TIMES_PER_WEEK needs timesPerWeek (1-6); DAILY needs neither")
public record FrequencyConfig(
        @Schema(example = "[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]") Set<DayOfWeek> days,
        @Schema(example = "3") Integer timesPerWeek) {

    /**
     * Checks the config fits the type and returns the form to store, or {@code null} for
     * {@code DAILY}. Fields that don't belong to the type are dropped rather than stored.
     */
    static FrequencyConfig normalize(FrequencyType type, FrequencyConfig config) {
        return switch (type) {
            case DAILY -> null;
            case SPECIFIC_DAYS -> {
                if (config == null || config.days() == null || config.days().isEmpty()) {
                    throw invalid("SPECIFIC_DAYS needs at least one day");
                }
                yield new FrequencyConfig(EnumSet.copyOf(config.days()), null);
            }
            case X_TIMES_PER_WEEK -> {
                // 7 a week is DAILY; 0 is not a habit
                if (config == null || config.timesPerWeek() == null
                        || config.timesPerWeek() < 1 || config.timesPerWeek() > 6) {
                    throw invalid("X_TIMES_PER_WEEK needs timesPerWeek between 1 and 6");
                }
                yield new FrequencyConfig(null, config.timesPerWeek());
            }
        };
    }

    private static ApplicationException invalid(String message) {
        return new ApplicationException(ErrorCode.HABIT_INVALID_FREQUENCY, message);
    }
}
