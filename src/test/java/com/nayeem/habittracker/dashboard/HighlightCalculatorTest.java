package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.checkin.StreakUnit;
import com.nayeem.habittracker.dashboard.DashboardResponse.AtRiskHabit;
import com.nayeem.habittracker.dashboard.DashboardResponse.HabitCompletion;
import com.nayeem.habittracker.dashboard.DashboardResponse.HabitRate;
import com.nayeem.habittracker.dashboard.DashboardResponse.Period;
import com.nayeem.habittracker.dashboard.DashboardResponse.TodayHabit;
import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.HabitKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HighlightCalculatorTest {

    /** A Friday: two days left in the week after today. */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 2);

    @Test
    void aDailyStreakIsAtRiskWhenDueAndNotDone() {
        List<AtRiskHabit> atRisk = HighlightCalculator.atRisk(List.of(
                daily(1L, 3, true, false),    // at risk
                daily(2L, 2, true, false),    // too short to warn about
                daily(3L, 9, true, true),     // done today
                daily(4L, 9, false, false)),  // not scheduled today
                FRIDAY);

        assertThat(atRisk).containsExactly(new AtRiskHabit(1L, "h1", 3, StreakUnit.DAYS, 1));
    }

    @Test
    void anNTimesAWeekStreakIsAtRiskWhenTheWeekBarelyHasRoomLeft() {
        List<AtRiskHabit> atRisk = HighlightCalculator.atRisk(List.of(
                weekly(1L, 0, false),   // needs 3, has today + 2 days: at risk
                weekly(2L, 1, false),   // needs 2, has 3 days: fine
                weekly(3L, 1, true),    // done today, needs 2 more, has 2 days: at risk
                weekly(4L, 3, true)),   // quota met
                FRIDAY);

        assertThat(atRisk).extracting(AtRiskHabit::habitId, AtRiskHabit::needed)
                .containsExactlyInAnyOrder(tuple(1L, 3), tuple(3L, 2));
    }

    @Test
    void longestStreakAtRiskComesFirst() {
        List<AtRiskHabit> atRisk = HighlightCalculator.atRisk(
                List.of(daily(1L, 4, true, false), daily(2L, 20, true, false)), FRIDAY);

        assertThat(atRisk).extracting(AtRiskHabit::habitId).containsExactly(2L, 1L);
    }

    @Test
    void bestAreTheTopThirtyDayRatesTiesKeepingNameOrder() {
        List<HabitRate> best = HighlightCalculator.best(List.of(
                completion(1L, 0.5, null), completion(2L, 0.9, 0.1), completion(3L, null, null),
                completion(4L, 0.9, null), completion(5L, 0.7, -0.2)));

        assertThat(best).extracting(HabitRate::habitId).containsExactly(2L, 4L, 5L);
    }

    @Test
    void slippingAreOnlyRealDropsBiggestFirst() {
        List<HabitRate> slipping = HighlightCalculator.slipping(List.of(
                completion(1L, 0.5, -0.1), completion(2L, 0.9, 0.2), completion(3L, 0.2, -0.6),
                completion(4L, 0.4, 0.0), completion(5L, 0.6, null)));

        assertThat(slipping).extracting(HabitRate::habitId).containsExactly(3L, 1L);
        assertThat(slipping.getFirst()).isEqualTo(new HabitRate(3L, "h3", 0.2, -0.6));
    }

    private static TodayHabit daily(Long id, int streak, boolean due, boolean done) {
        return new TodayHabit(id, "h" + id, null, HabitKind.BUILD, FrequencyType.DAILY, 1, null, done ? 1 : 0, done,
                false, due, null, null, streak, StreakUnit.DAYS);
    }

    private static TodayHabit weekly(Long id, int doneThisWeek, boolean doneToday) {
        return new TodayHabit(id, "h" + id, null, HabitKind.BUILD, FrequencyType.X_TIMES_PER_WEEK, 1, null,
                doneToday ? 1 : 0, doneToday, false, true, doneThisWeek, 3, 5, StreakUnit.WEEKS);
    }

    private static HabitCompletion completion(Long id, Double rate30, Double change30) {
        Period empty = new Period(7, 0, 0, null, null, null);
        return new HabitCompletion(id, "h" + id, empty, new Period(30, 0, 0, rate30, null, change30), empty);
    }
}
