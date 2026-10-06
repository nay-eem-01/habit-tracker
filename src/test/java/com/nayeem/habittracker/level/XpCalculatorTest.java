package com.nayeem.habittracker.level;

import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class XpCalculatorTest {

    /** A Monday. */
    private static final LocalDate START = LocalDate.of(2026, 9, 7);

    @Nested
    class Daily {

        @Test
        void nothingDoneIsZero() {
            assertThat(xp(Set.of(), START.plusDays(10))).isZero();
        }

        @Test
        void tenPerDoneDayBeforeAWeeksStreak() {
            assertThat(xp(days(0, 3), START.plusDays(5))).isEqualTo(30);
        }

        @Test
        void theSeventhDayInARowBringsTheBonusAndTheFirstMilestone() {
            // days 1–6: 6 × 10; day 7: 10 + 5 + 50
            assertThat(xp(days(0, 7), START.plusDays(6))).isEqualTo(125);
            // day 8: 10 + 5
            assertThat(xp(days(0, 8), START.plusDays(7))).isEqualTo(140);
        }

        @Test
        void aBrokenStreakTakesNothingBack() {
            Set<LocalDate> done = days(0, 7);
            done.add(START.plusDays(8));   // day 8 missed, day 9 done: a new run of 1

            assertThat(xp(done, START.plusDays(8))).isEqualTo(125 + 10);
        }

        @Test
        void aNewRunCanEarnTheMilestonesAgain() {
            Set<LocalDate> done = days(0, 7);
            done.addAll(days(8, 7));   // day 8 missed, then 7 more in a row

            assertThat(xp(done, START.plusDays(14))).isEqualTo(125 * 2);
        }

        @Test
        void thirtyDaysInARow() {
            // 6 × 10, then 24 × 15, plus the 7- and 30-day milestones
            assertThat(xp(days(0, 30), START.plusDays(29))).isEqualTo(60 + 360 + 50 + 200);
        }

        @Test
        void todayNotDoneYetCostsNothing() {
            assertThat(xp(days(0, 7), START.plusDays(7))).isEqualTo(125);
        }

        private long xp(Set<LocalDate> done, LocalDate today) {
            return XpCalculator.habitXp(FrequencyType.DAILY, null, done, START, today);
        }
    }

    @Nested
    class SpecificDays {

        private final FrequencyConfig monWedFri =
                new FrequencyConfig(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), null);

        @Test
        void aCheckInOnAnUnscheduledDayEarnsNothing() {
            // Monday and Wednesday are scheduled; Tuesday isn't
            Set<LocalDate> done = Set.of(START, START.plusDays(1), START.plusDays(2));

            assertThat(XpCalculator.habitXp(FrequencyType.SPECIFIC_DAYS, monWedFri, done, START, START.plusDays(2)))
                    .isEqualTo(20);
        }
    }

    @Nested
    class TimesPerWeek {

        private final FrequencyConfig threeTimes = new FrequencyConfig(null, 3);

        @Test
        void aFullWeekIsAWeeksStreakAtOnce() {
            // 3 × (10 + 5) + the 1-week milestone
            assertThat(xp(days(0, 3), START.plusDays(6))).isEqualTo(95);
        }

        @Test
        void extraDaysInAWeekDontCount() {
            assertThat(xp(days(0, 5), START.plusDays(6))).isEqualTo(95);
        }

        @Test
        void theWeekInProgressKeepsTheBonusOfTheRunSoFar() {
            Set<LocalDate> done = days(0, 3);
            done.addAll(days(7, 2));   // 2 of 3 so far in week 2, still going

            assertThat(xp(done, START.plusDays(9))).isEqualTo(95 + 2 * 15);
        }

        @Test
        void aMissedWeekEndsTheBonusButKeepsTheXp() {
            Set<LocalDate> done = days(0, 3);
            done.addAll(days(7, 2));    // week 2 ended at 2 of 3: the run is over
            done.addAll(days(14, 1));   // week 3: a new run hasn't started yet

            assertThat(xp(done, START.plusDays(15))).isEqualTo(95 + 2 * 10 + 10);
        }

        @Test
        void fourFullWeeksReachTheSecondMilestone() {
            Set<LocalDate> done = new HashSet<>();
            for (int week = 0; week < 4; week++) {
                done.addAll(days(week * 7, 3));
            }

            assertThat(xp(done, START.plusDays(27))).isEqualTo(12 * 15 + 50 + 200);
        }

        private long xp(Set<LocalDate> done, LocalDate today) {
            return XpCalculator.habitXp(FrequencyType.X_TIMES_PER_WEEK, threeTimes, done, START, today);
        }
    }

    @Test
    void fiveHundredPerAchievedGoal() {
        assertThat(XpCalculator.goalXp(0)).isZero();
        assertThat(XpCalculator.goalXp(2)).isEqualTo(1000);
    }

    /** {@code count} days in a row, starting {@code from} days after {@link #START}. */
    private static Set<LocalDate> days(int from, int count) {
        Set<LocalDate> days = new HashSet<>();
        for (int i = 0; i < count; i++) {
            days.add(START.plusDays(from + i));
        }
        return days;
    }
}
