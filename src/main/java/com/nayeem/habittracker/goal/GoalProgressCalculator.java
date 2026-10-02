package com.nayeem.habittracker.goal;

import java.util.List;

/**
 * Goal progress maths (PLAN.md §11.1), plain Java with no Spring or database so it is unit-tested
 * directly. Progress only goes up: a missed day slows it down, it never takes anything back.
 */
final class GoalProgressCalculator {

    private GoalProgressCalculator() {
    }

    /** Share of the target reached, 0–1: {@code min(doneDays / targetDays, 1)}. */
    static double habitFraction(long doneDays, int targetDays) {
        if (targetDays <= 0) {
            return 0;
        }
        return Math.min((double) doneDays / targetDays, 1.0);
    }

    /** The goal's progress: the average of its habits' fractions; no habits = 0. */
    static double goalFraction(List<Double> habitFractions) {
        return habitFractions.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /** A 0–1 fraction as a whole percent, rounded half up. */
    static int percent(double fraction) {
        return (int) Math.round(fraction * 100);
    }
}
