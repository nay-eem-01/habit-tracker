package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.checkin.CheckInTime;
import com.nayeem.habittracker.dashboard.PatternsResponse.HeatmapDay;
import com.nayeem.habittracker.dashboard.PatternsResponse.WeekdayRate;
import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PatternCalculatorTest {

    /** A Wednesday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate LONG_AGO = TODAY.minusYears(2);

    @Test
    void heatmapCoversAYearEndingToday() {
        List<HeatmapDay> heatmap = PatternCalculator.heatmap(List.of(), TODAY);

        assertThat(heatmap).hasSize(365);
        assertThat(heatmap.getFirst().date()).isEqualTo(TODAY.minusDays(364));
        assertThat(heatmap.getLast().date()).isEqualTo(TODAY);
        assertThat(heatmap.getLast().ratio()).isNull();
    }

    @Test
    void heatmapCountsScheduledHabitsFromTheirFirstDay() {
        HabitDays daily = new HabitDays(FrequencyType.DAILY, null, TODAY.minusDays(2), Set.of(TODAY.minusDays(1)));
        HabitDays monWed = new HabitDays(FrequencyType.SPECIFIC_DAYS,
                new FrequencyConfig(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), null), LONG_AGO, Set.of(TODAY));

        List<HeatmapDay> heatmap = PatternCalculator.heatmap(List.of(daily, monWed), TODAY);

        assertThat(day(heatmap, TODAY)).isEqualTo(new HeatmapDay(TODAY, 1, 2, 0.5));                   // Wed: both
        assertThat(day(heatmap, TODAY.minusDays(1))).isEqualTo(new HeatmapDay(TODAY.minusDays(1), 1, 1, 1.0));  // Tue
        assertThat(day(heatmap, TODAY.minusDays(2))).isEqualTo(new HeatmapDay(TODAY.minusDays(2), 0, 2, 0.0));  // Mon
        assertThat(day(heatmap, TODAY.minusDays(3))).isEqualTo(new HeatmapDay(TODAY.minusDays(3), 0, 0, null)); // before
    }

    @Test
    void anNTimesAWeekHabitOnlyAddsToTheDaysItWasDone() {
        HabitDays weekly = new HabitDays(FrequencyType.X_TIMES_PER_WEEK, new FrequencyConfig(null, 3), LONG_AGO,
                Set.of(TODAY.minusDays(1)));

        List<HeatmapDay> heatmap = PatternCalculator.heatmap(List.of(weekly), TODAY);

        assertThat(day(heatmap, TODAY.minusDays(1)).ratio()).isEqualTo(1.0);
        assertThat(day(heatmap, TODAY).expected()).isZero();
    }

    @Test
    void weekdayRatesOverTwelveFullWeeksBeforeToday() {
        // a daily habit done every day except Mondays; today (Wednesday) not done and not counted
        Set<LocalDate> done = new HashSet<>();
        for (LocalDate day = TODAY.minusDays(84); day.isBefore(TODAY); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.MONDAY) {
                done.add(day);
            }
        }
        HabitDays daily = new HabitDays(FrequencyType.DAILY, null, LONG_AGO, done);
        HabitDays weekly = new HabitDays(FrequencyType.X_TIMES_PER_WEEK, new FrequencyConfig(null, 2), LONG_AGO, Set.of());

        List<WeekdayRate> rates = PatternCalculator.weekdays(List.of(daily, weekly), TODAY);

        assertThat(rates).extracting(WeekdayRate::day).containsExactly(DayOfWeek.values());
        assertThat(rates.getFirst()).isEqualTo(new WeekdayRate(DayOfWeek.MONDAY, 0, 12, 0.0));
        assertThat(rates.get(2)).isEqualTo(new WeekdayRate(DayOfWeek.WEDNESDAY, 12, 12, 1.0));
        assertThat(PatternCalculator.weakest(rates)).isEqualTo(DayOfWeek.MONDAY);
        assertThat(PatternCalculator.strongest(rates)).isEqualTo(DayOfWeek.TUESDAY);   // tie: the earlier day
    }

    @Test
    void noWeakestOrStrongestDayWithoutData() {
        List<WeekdayRate> rates = PatternCalculator.weekdays(List.of(), TODAY);

        assertThat(PatternCalculator.weakest(rates)).isNull();
        assertThat(PatternCalculator.strongest(rates)).isNull();
    }

    @Test
    void hoursAreInTheUsersTimezoneAndSkipCatchUps() {
        ZoneId dhaka = ZoneId.of("Asia/Dhaka");   // UTC+6
        List<CheckInTime> checkIns = List.of(
                new CheckInTime(1L, TODAY, Instant.parse("2026-09-30T01:30:00Z")),               // 07:30 local
                new CheckInTime(2L, TODAY, Instant.parse("2026-09-29T20:10:00Z")),               // 02:10 local, same day
                new CheckInTime(3L, TODAY.minusDays(1), Instant.parse("2026-09-30T01:45:00Z")),  // logged a day late
                new CheckInTime(4L, TODAY.minusDays(1), Instant.parse("2026-09-29T01:00:00Z"))); // 07:00 local

        int[] hours = PatternCalculator.hours(checkIns, dhaka);

        assertThat(hours[7]).isEqualTo(2);
        assertThat(hours[2]).isEqualTo(1);
        assertThat(PatternCalculator.asList(hours).stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        assertThat(PatternCalculator.peakHour(hours)).isEqualTo(7);
    }

    @Test
    void peakHourTiesGoToTheEarlierHourAndIsNullWithoutCheckIns() {
        int[] hours = new int[24];
        assertThat(PatternCalculator.peakHour(hours)).isNull();
        hours[21] = 2;
        hours[8] = 2;
        assertThat(PatternCalculator.peakHour(hours)).isEqualTo(8);
    }

    private static HeatmapDay day(List<HeatmapDay> heatmap, LocalDate date) {
        return heatmap.stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }
}
