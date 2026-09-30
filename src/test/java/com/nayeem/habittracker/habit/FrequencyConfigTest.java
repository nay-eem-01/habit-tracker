package com.nayeem.habittracker.habit;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.util.Set;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrequencyConfigTest {

    @Test
    void dailyStoresNoConfigEvenIfOneIsSent() {
        assertThat(FrequencyConfig.normalize(FrequencyType.DAILY, new FrequencyConfig(Set.of(MONDAY), 3))).isNull();
        assertThat(FrequencyConfig.normalize(FrequencyType.DAILY, null)).isNull();
    }

    @Test
    void specificDaysKeepsOnlyTheDays() {
        FrequencyConfig config = FrequencyConfig.normalize(FrequencyType.SPECIFIC_DAYS,
                new FrequencyConfig(Set.of(FRIDAY, MONDAY), 3));
        assertThat(config.days()).containsExactly(MONDAY, FRIDAY);
        assertThat(config.timesPerWeek()).isNull();
    }

    @Test
    void specificDaysNeedsAtLeastOneDay() {
        assertInvalid(FrequencyType.SPECIFIC_DAYS, null);
        assertInvalid(FrequencyType.SPECIFIC_DAYS, new FrequencyConfig(Set.<DayOfWeek>of(), null));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6})
    void timesPerWeekAcceptsOneToSix(int times) {
        FrequencyConfig config = FrequencyConfig.normalize(FrequencyType.X_TIMES_PER_WEEK,
                new FrequencyConfig(Set.of(MONDAY), times));
        assertThat(config.timesPerWeek()).isEqualTo(times);
        assertThat(config.days()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, -1})
    void timesPerWeekOutsideOneToSixIsRejected(int times) {
        assertInvalid(FrequencyType.X_TIMES_PER_WEEK, new FrequencyConfig(null, times));
    }

    @Test
    void timesPerWeekIsRequired() {
        assertInvalid(FrequencyType.X_TIMES_PER_WEEK, null);
    }

    private static void assertInvalid(FrequencyType type, FrequencyConfig config) {
        assertThatThrownBy(() -> FrequencyConfig.normalize(type, config))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HABIT_INVALID_FREQUENCY);
    }
}
