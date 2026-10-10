package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

class StatsCalculatorTest {

    /** Wednesday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate LONG_AGO = TODAY.minusYears(1);

    @Test
    void dailyAllDoneIsOne() {
        WindowStats stats = daily(lastDays(7), LONG_AGO, 7);
        assertThat(stats).isEqualTo(new WindowStats(7, 7, 7, 1.0));
    }

    @Test
    void todayNotDoneYetIsLeftOut() {
        // the last 6 days done, today not yet
        WindowStats stats = daily(daysAgo(1, 2, 3, 4, 5, 6), LONG_AGO, 7);
        assertThat(stats).isEqualTo(new WindowStats(7, 6, 6, 1.0));
    }

    @Test
    void halfDone() {
        WindowStats stats = daily(daysAgo(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28), LONG_AGO, 30);
        assertThat(stats.done()).isEqualTo(15);
        assertThat(stats.expected()).isEqualTo(30);
        assertThat(stats.rate()).isEqualTo(0.5);
    }

    @Test
    void windowIsClippedToTheHabitsFirstDay() {
        // created 2 days ago, done every day since
        WindowStats stats = daily(daysAgo(0, 1, 2), TODAY.minusDays(2), 30);
        assertThat(stats).isEqualTo(new WindowStats(30, 3, 3, 1.0));
    }

    @Test
    void createdTodayAndNotDoneHasNoRateYet() {
        assertThat(daily(Set.of(), TODAY, 7).rate()).isNull();
    }

    @Test
    void specificDaysCountOnlyScheduledDays() {
        // Mon + Wed habit; the 14 days to Wed 30th (from Thu 17th) schedule Mon 21, Wed 23, Mon 28, Wed 30
        FrequencyConfig monWed = new FrequencyConfig(Set.of(MONDAY, WEDNESDAY), null);
        Set<LocalDate> done = Set.of(TODAY, TODAY.minusDays(2), TODAY.minusDays(1)); // Tue 29th is unscheduled
        WindowStats stats = StatsCalculator.window(FrequencyType.SPECIFIC_DAYS, monWed, done, LONG_AGO, TODAY, 14);
        assertThat(stats).isEqualTo(new WindowStats(14, 2, 4, 0.5));
    }

    @Test
    void timesPerWeekIsCappedAtOne() {
        FrequencyConfig three = new FrequencyConfig(null, 3);
        WindowStats stats = StatsCalculator.window(FrequencyType.X_TIMES_PER_WEEK, three, lastDays(7), LONG_AGO, TODAY, 7);
        assertThat(stats).isEqualTo(new WindowStats(7, 7, 3, 1.0));
    }

    @Test
    void timesPerWeekOverThirtyDays() {
        FrequencyConfig three = new FrequencyConfig(null, 3);
        // 9 done days, today among them; expected 3 x 30 / 7 = 12.86
        WindowStats stats = StatsCalculator.window(FrequencyType.X_TIMES_PER_WEEK, three,
                daysAgo(0, 3, 6, 9, 12, 15, 18, 21, 24), LONG_AGO, TODAY, 30);
        assertThat(stats.expected()).isEqualTo(12.86);
        assertThat(stats.rate()).isEqualTo(0.7);
    }

    @Test
    void aRestDayIsNotExpected() {
        // last 7 days: done 5, rested 1, today open
        WindowStats stats = StatsCalculator.window(FrequencyType.DAILY, null, daysAgo(1, 2, 3, 4, 5), daysAgo(6),
                LONG_AGO, TODAY, 7);
        assertThat(stats.expected()).isEqualTo(5.0);
        assertThat(stats.rate()).isEqualTo(1.0);
    }

    @Test
    void previousWindowIsTheSameLengthJustBefore() {
        // previous 7 days = 13..7 days ago: 7–10 done (4 of 7); this week's check-ins don't count there
        Set<LocalDate> done = daysAgo(0, 1, 7, 8, 9, 10);

        assertThat(StatsCalculator.previousWindow(FrequencyType.DAILY, null, done, LONG_AGO, TODAY, 7))
                .isEqualTo(new WindowStats(7, 4, 7, 0.57));
    }

    @Test
    void previousWindowCountsItsLastDayEvenWhenUndone() {
        // 7 days ago is the previous window's last day: over, so it counts against the rate
        Set<LocalDate> done = daysAgo(8, 9, 10, 11, 12, 13);

        assertThat(StatsCalculator.previousWindow(FrequencyType.DAILY, null, done, LONG_AGO, TODAY, 7))
                .isEqualTo(new WindowStats(7, 6, 7, 0.86));
    }

    @Test
    void previousWindowOfAYoungHabitIsClippedOrEmpty() {
        // created 9 days ago: the previous window (13..7 days ago) has only 9..7 days ago
        assertThat(StatsCalculator.previousWindow(FrequencyType.DAILY, null, daysAgo(8), TODAY.minusDays(9), TODAY, 7))
                .isEqualTo(new WindowStats(7, 1, 3, 0.33));
        // created 3 days ago: nothing to compare with
        assertThat(StatsCalculator.previousWindow(FrequencyType.DAILY, null, Set.of(), TODAY.minusDays(3), TODAY, 7))
                .isEqualTo(new WindowStats(7, 0, 0, null));
    }

    private static WindowStats daily(Set<LocalDate> done, LocalDate start, int days) {
        return StatsCalculator.window(FrequencyType.DAILY, null, done, start, TODAY, days);
    }

    private static Set<LocalDate> lastDays(int count) {
        return IntStream.range(0, count).mapToObj(TODAY::minusDays).collect(Collectors.toSet());
    }

    private static Set<LocalDate> daysAgo(int... offsets) {
        return IntStream.of(offsets).mapToObj(TODAY::minusDays).collect(Collectors.toSet());
    }
}
