package com.nayeem.habittracker.goal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalProgressCalculatorTest {

    @Test
    void aHabitIsDoneDaysOverTarget() {
        assertThat(GoalProgressCalculator.habitFraction(0, 60)).isZero();
        assertThat(GoalProgressCalculator.habitFraction(15, 60)).isEqualTo(0.25);
        assertThat(GoalProgressCalculator.habitFraction(60, 60)).isEqualTo(1.0);
    }

    @Test
    void aHabitIsCappedAtFull() {
        assertThat(GoalProgressCalculator.habitFraction(90, 60)).isEqualTo(1.0);
    }

    @Test
    void aNonsenseTargetCountsAsNothingRatherThanDividingByZero() {
        assertThat(GoalProgressCalculator.habitFraction(5, 0)).isZero();
    }

    @Test
    void aGoalIsTheAverageOfItsHabits() {
        assertThat(GoalProgressCalculator.goalFraction(List.of(1.0, 0.5, 0.0))).isEqualTo(0.5);
    }

    @Test
    void aGoalWithNoHabitsIsAtZero() {
        assertThat(GoalProgressCalculator.goalFraction(List.of())).isZero();
    }

    @Test
    void percentRoundsHalfUp() {
        assertThat(GoalProgressCalculator.percent(1.0 / 3)).isEqualTo(33);
        assertThat(GoalProgressCalculator.percent(2.0 / 3)).isEqualTo(67);
        assertThat(GoalProgressCalculator.percent(0.125)).isEqualTo(13);
        assertThat(GoalProgressCalculator.percent(1.0)).isEqualTo(100);
    }

    @Test
    void moreDoneDaysNeverLowersProgress() {
        double previous = 0;
        for (long done = 0; done <= 70; done++) {
            double now = GoalProgressCalculator.habitFraction(done, 60);
            assertThat(now).isGreaterThanOrEqualTo(previous);
            previous = now;
        }
    }
}
