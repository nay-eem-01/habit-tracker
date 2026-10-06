package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.checkin.WindowStats;
import com.nayeem.habittracker.dashboard.DashboardResponse.Period;

import java.util.List;

/** Turns window stats into the dashboard's periods. Pure, like the other calculators. */
final class CompletionCalculator {

    private CompletionCalculator() {
    }

    static Period period(WindowStats current, WindowStats previous) {
        return new Period(current.days(), current.done(), current.expected(), current.rate(), previous.rate(),
                change(current.rate(), previous.rate()));
    }

    /**
     * Several habits as one: done and expected add up. The rate caps each habit at what it was asked for,
     * so extra check-ins on an N-a-week habit don't make up for another habit's missed days.
     */
    static Period overall(int days, List<WindowStats> current, List<WindowStats> previous) {
        Double rate = combinedRate(current);
        Double previousRate = combinedRate(previous);
        return new Period(days, current.stream().mapToInt(WindowStats::done).sum(),
                round2(current.stream().mapToDouble(WindowStats::expected).sum()), rate, previousRate,
                change(rate, previousRate));
    }

    private static Double combinedRate(List<WindowStats> windows) {
        double expected = windows.stream().mapToDouble(WindowStats::expected).sum();
        if (expected == 0) {
            return null;
        }
        double counted = windows.stream().mapToDouble(w -> Math.min(w.done(), w.expected())).sum();
        return round2(counted / expected);
    }

    private static Double change(Double rate, Double previousRate) {
        return rate == null || previousRate == null ? null : round2(rate - previousRate);
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
