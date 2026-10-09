package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

class StreakCalculatorTest {

    /** 2026-09-28 is a Monday; 2026-09-30 a Wednesday. */
    private static final LocalDate MON = LocalDate.of(2026, 9, 28);
    private static final LocalDate WED = MON.plusDays(2);

    @Nested
    class Daily {

        @Test
        void noLogsIsZero() {
            assertThat(daily(Set.of(), WED.minusDays(10), WED)).isEqualTo(new Streak(0, 0, StreakUnit.DAYS));
        }

        @Test
        void consecutiveDaysEndingToday() {
            assertThat(daily(days(WED, -2, -1, 0), WED.minusDays(10), WED).current()).isEqualTo(3);
        }

        @Test
        void todayNotDoneYetDoesNotBreakIt() {
            Streak streak = daily(days(WED, -3, -2, -1), WED.minusDays(10), WED);
            assertThat(streak.current()).isEqualTo(3);
        }

        @Test
        void aRestDayNeitherBreaksNorExtendsTheRun() {
            // done 3 and 1 days ago, rested 2 days ago
            Streak streak = StreakCalculator.calculate(FrequencyType.DAILY, null, days(WED, -3, -1), days(WED, -2),
                    WED.minusDays(3), WED);
            assertThat(streak.current()).isEqualTo(2);
        }

        @Test
        void aMissedDayResetsItToZero() {
            // done 5 and 4 days ago, missed 3 days ago, done the last two
            Streak streak = daily(days(WED, -5, -4, -2, -1), WED.minusDays(10), WED);
            assertThat(streak.current()).isEqualTo(2);
            assertThat(streak.longest()).isEqualTo(2);
        }

        @Test
        void missingYesterdayMeansCurrentIsZeroButLongestRemains() {
            Streak streak = daily(days(WED, -6, -5, -4, -3, -2), WED.minusDays(10), WED);
            assertThat(streak.current()).isZero();
            assertThat(streak.longest()).isEqualTo(5);
        }

        @Test
        void crossesMonthAndYearBoundaries() {
            LocalDate jan2 = LocalDate.of(2027, 1, 2);
            Streak streak = daily(days(jan2, -3, -2, -1, 0), LocalDate.of(2026, 12, 29), jan2);
            assertThat(streak.current()).isEqualTo(4);
        }

        @Test
        void habitCreatedTodayAndDone() {
            assertThat(daily(Set.of(WED), WED, WED)).isEqualTo(new Streak(1, 1, StreakUnit.DAYS));
        }

        private Streak daily(Set<LocalDate> done, LocalDate start, LocalDate today) {
            return StreakCalculator.calculate(FrequencyType.DAILY, null, done, start, today);
        }
    }

    @Nested
    class SpecificDays {

        private final FrequencyConfig monWedFri = new FrequencyConfig(Set.of(MONDAY, WEDNESDAY, FRIDAY), null);

        @Test
        void unscheduledDaysAreSkipped() {
            // Fri 25th, Mon 28th, Wed 30th: three scheduled days in a row, with the weekend and Tue between
            Set<LocalDate> done = Set.of(MON.minusDays(3), MON, WED);
            assertThat(calc(done, MON.minusDays(14), WED).current()).isEqualTo(3);
        }

        @Test
        void aMissedScheduledDayBreaksIt() {
            // Fri 25th missed
            Set<LocalDate> done = Set.of(MON.minusDays(5), MON, WED);
            assertThat(calc(done, MON.minusDays(14), WED).current()).isEqualTo(2);
        }

        @Test
        void checkInOnAnUnscheduledDayNeitherCountsNorBreaks() {
            Set<LocalDate> done = Set.of(MON, MON.plusDays(1), WED); // Tuesday is not scheduled
            assertThat(calc(done, MON, WED).current()).isEqualTo(2);
        }

        @Test
        void unscheduledTodayKeepsTheStreak() {
            // today is Tuesday (unscheduled); Monday was done
            assertThat(calc(Set.of(MON), MON, MON.plusDays(1)).current()).isEqualTo(1);
        }

        private Streak calc(Set<LocalDate> done, LocalDate start, LocalDate today) {
            return StreakCalculator.calculate(FrequencyType.SPECIFIC_DAYS, monWedFri, done, start, today);
        }
    }

    @Nested
    class TimesPerWeek {

        private final FrequencyConfig threeTimes = new FrequencyConfig(null, 3);

        @Test
        void countsCompletedWeeks() {
            Set<LocalDate> done = union(
                    week(MON.minusWeeks(2), 0, 2, 4),
                    week(MON.minusWeeks(1), 1, 3, 5));
            Streak streak = calc(done, MON.minusWeeks(2), WED);
            assertThat(streak).isEqualTo(new Streak(2, 2, StreakUnit.WEEKS));
        }

        @Test
        void currentWeekCountsOnceMet() {
            Set<LocalDate> done = union(week(MON.minusWeeks(1), 0, 1, 2), week(MON, 0, 1, 2));
            assertThat(calc(done, MON.minusWeeks(1), WED).current()).isEqualTo(2);
        }

        @Test
        void aShortWeekBreaksIt() {
            Set<LocalDate> done = union(
                    week(MON.minusWeeks(3), 0, 1, 2),
                    week(MON.minusWeeks(2), 0, 1),       // only 2 of 3
                    week(MON.minusWeeks(1), 0, 1, 2));
            Streak streak = calc(done, MON.minusWeeks(3), WED);
            assertThat(streak.current()).isEqualTo(1);
            assertThat(streak.longest()).isEqualTo(1);
        }

        @Test
        void firstPartialWeekDoesNotBreakIt() {
            // created on the Saturday of an earlier week, only 1 done that week
            LocalDate saturday = MON.minusWeeks(1).minusDays(2);
            Set<LocalDate> done = union(Set.of(saturday), week(MON.minusWeeks(1), 0, 2, 4));
            assertThat(calc(done, saturday, WED).current()).isEqualTo(1);
        }

        @Test
        void extraDaysInAWeekDoNotCountTwice() {
            Set<LocalDate> done = week(MON.minusWeeks(1), 0, 1, 2, 3, 4, 5, 6);
            assertThat(calc(done, MON.minusWeeks(1), WED).current()).isEqualTo(1);
        }

        private Streak calc(Set<LocalDate> done, LocalDate start, LocalDate today) {
            return StreakCalculator.calculate(FrequencyType.X_TIMES_PER_WEEK, threeTimes, done, start, today);
        }
    }

    /** Days relative to {@code base}, e.g. {@code days(WED, -1, 0)} = Tue and Wed. */
    private static Set<LocalDate> days(LocalDate base, int... offsets) {
        return Arrays.stream(offsets).mapToObj(base::plusDays).collect(Collectors.toSet());
    }

    /** Days of the week starting at {@code monday}, 0 = Monday. */
    private static Set<LocalDate> week(LocalDate monday, int... dayIndexes) {
        return days(monday, dayIndexes);
    }

    @SafeVarargs
    private static Set<LocalDate> union(Set<LocalDate>... sets) {
        return Arrays.stream(sets).flatMap(Set::stream).collect(Collectors.toSet());
    }
}
