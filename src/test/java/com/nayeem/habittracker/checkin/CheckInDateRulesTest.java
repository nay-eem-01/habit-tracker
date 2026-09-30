package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckInDateRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate LONG_AGO = LocalDate.of(2026, 1, 1);

    @Test
    void todayAndUpToSevenDaysBackAreAllowed() {
        assertThatCode(() -> CheckInService.checkDateAllowed(TODAY, TODAY, LONG_AGO)).doesNotThrowAnyException();
        assertThatCode(() -> CheckInService.checkDateAllowed(TODAY.minusDays(7), TODAY, LONG_AGO)).doesNotThrowAnyException();
    }

    @Test
    void tomorrowIsRejected() {
        assertThatThrownBy(() -> CheckInService.checkDateAllowed(TODAY.plusDays(1), TODAY, LONG_AGO))
                .isInstanceOf(ApplicationException.class).hasMessageContaining("future");
    }

    @Test
    void eightDaysBackIsRejected() {
        assertThatThrownBy(() -> CheckInService.checkDateAllowed(TODAY.minusDays(8), TODAY, LONG_AGO))
                .isInstanceOf(ApplicationException.class).hasMessageContaining("7 days");
    }

    @Test
    void beforeTheHabitExistedIsRejected() {
        assertThatThrownBy(() -> CheckInService.checkDateAllowed(TODAY.minusDays(1), TODAY, TODAY))
                .isInstanceOf(ApplicationException.class).hasMessageContaining("didn't exist");
    }
}
