package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.checkin.CheckInTime;
import com.nayeem.habittracker.dashboard.PatternsResponse.HeatmapDay;
import com.nayeem.habittracker.dashboard.PatternsResponse.WeekdayRate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Heatmap, weekday and time-of-day patterns (PLAN.md §11.4). Pure: no Spring, no database, today passed
 * in. N-a-week habits have no fixed days, so they add to a day when done and never count against one.
 */
final class PatternCalculator {

    static final int HEATMAP_DAYS = 365;
    static final int WEEKDAY_WEEKS = 12;
    static final int HOURS_DAYS = 90;

    private PatternCalculator() {
    }

    /** {@value #HEATMAP_DAYS} days ending today, oldest first. */
    static List<HeatmapDay> heatmap(Collection<HabitDays> habits, LocalDate today) {
        List<HeatmapDay> days = new ArrayList<>(HEATMAP_DAYS);
        for (LocalDate day = today.minusDays(HEATMAP_DAYS - 1L); !day.isAfter(today); day = day.plusDays(1)) {
            int done = 0;
            int expected = 0;
            for (HabitDays habit : habits) {
                boolean doneThatDay = habit.doneDays().contains(day);
                if (habit.scheduledOn(day) || (habit.weekly() && doneThatDay)) {
                    expected++;
                    if (doneThatDay) {
                        done++;
                    }
                }
            }
            days.add(new HeatmapDay(day, done, expected, ratio(done, expected)));
        }
        return days;
    }

    /** Monday to Sunday over the {@value #WEEKDAY_WEEKS} full weeks of days before today. */
    static List<WeekdayRate> weekdays(Collection<HabitDays> habits, LocalDate today) {
        int[] done = new int[7];
        int[] expected = new int[7];
        for (LocalDate day = today.minusDays(WEEKDAY_WEEKS * 7L); day.isBefore(today); day = day.plusDays(1)) {
            int i = day.getDayOfWeek().ordinal();
            for (HabitDays habit : habits) {
                if (habit.scheduledOn(day)) {
                    expected[i]++;
                    if (habit.doneDays().contains(day)) {
                        done[i]++;
                    }
                }
            }
        }
        List<WeekdayRate> rates = new ArrayList<>(7);
        for (DayOfWeek day : DayOfWeek.values()) {
            rates.add(new WeekdayRate(day, done[day.ordinal()], expected[day.ordinal()],
                    ratio(done[day.ordinal()], expected[day.ordinal()])));
        }
        return rates;
    }

    static DayOfWeek weakest(List<WeekdayRate> rates) {
        return pick(rates, Comparator.comparingDouble(WeekdayRate::rate));
    }

    static DayOfWeek strongest(List<WeekdayRate> rates) {
        return pick(rates, Comparator.comparingDouble(WeekdayRate::rate).reversed());
    }

    /**
     * Check-ins per hour of the day in {@code zone}, from when each done day was first logged. A
     * check-in logged on a later day (catching up) says nothing about the time of day, so it's skipped.
     */
    static int[] hours(Collection<CheckInTime> checkIns, ZoneId zone) {
        int[] hours = new int[24];
        for (CheckInTime checkIn : checkIns) {
            ZonedDateTime at = checkIn.firstLoggedAt().atZone(zone);
            if (at.toLocalDate().equals(checkIn.day())) {
                hours[at.getHour()]++;
            }
        }
        return hours;
    }

    static Integer peakHour(int[] hours) {
        int peak = 0;
        for (int hour = 1; hour < 24; hour++) {
            if (hours[hour] > hours[peak]) {
                peak = hour;
            }
        }
        return hours[peak] == 0 ? null : peak;
    }

    private static DayOfWeek pick(List<WeekdayRate> rates, Comparator<WeekdayRate> order) {
        // stable on Monday-first input, so a tie goes to the earlier weekday
        Optional<WeekdayRate> first = rates.stream().filter(r -> r.rate() != null).min(order);
        return first.map(WeekdayRate::day).orElse(null);
    }

    private static Double ratio(int done, int expected) {
        return expected == 0 ? null : Math.round(done * 100.0 / expected) / 100.0;
    }

    static List<Integer> asList(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }
}
