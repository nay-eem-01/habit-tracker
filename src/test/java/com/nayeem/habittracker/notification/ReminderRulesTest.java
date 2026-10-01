package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderRulesTest {

    private static final LocalDate TUESDAY = LocalDate.of(2030, 1, 1);

    @Test
    void dailyIsAlwaysDue() {
        assertTrue(ReminderRules.isDue(FrequencyType.DAILY, null, TUESDAY, 0));
    }

    @Test
    void specificDaysAreDueOnlyOnTheirWeekdays() {
        var config = new FrequencyConfig(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), null);
        assertTrue(ReminderRules.isDue(FrequencyType.SPECIFIC_DAYS, config, TUESDAY, 0));
        assertFalse(ReminderRules.isDue(FrequencyType.SPECIFIC_DAYS, config, TUESDAY.plusDays(1), 0));
    }

    @Test
    void timesPerWeekIsDueUntilTheQuotaIsMet() {
        var config = new FrequencyConfig(null, 3);
        assertTrue(ReminderRules.isDue(FrequencyType.X_TIMES_PER_WEEK, config, TUESDAY, 0));
        assertTrue(ReminderRules.isDue(FrequencyType.X_TIMES_PER_WEEK, config, TUESDAY, 2));
        assertFalse(ReminderRules.isDue(FrequencyType.X_TIMES_PER_WEEK, config, TUESDAY, 3));
    }
}
