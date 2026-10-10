package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;

import java.time.LocalDate;
import java.util.Set;

/** What the pattern calculator needs of one habit: its schedule, first day and done days. */
record HabitDays(FrequencyType type, FrequencyConfig config, LocalDate start, Set<LocalDate> doneDays,
                 Set<LocalDate> restDays) {

    HabitDays(FrequencyType type, FrequencyConfig config, LocalDate start, Set<LocalDate> doneDays) {
        this(type, config, start, doneDays, Set.of());
    }

    boolean weekly() {
        return type == FrequencyType.X_TIMES_PER_WEEK;
    }

    /** Daily, or chosen weekdays on one of them, not rested; never an N-a-week habit (it has no fixed days). */
    boolean scheduledOn(LocalDate day) {
        return !day.isBefore(start) && !restDays.contains(day) && switch (type) {
            case DAILY -> true;
            case SPECIFIC_DAYS -> config.days().contains(day.getDayOfWeek());
            case X_TIMES_PER_WEEK -> false;
        };
    }
}
