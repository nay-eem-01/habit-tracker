package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.checkin.CheckInTime;
import com.nayeem.habittracker.checkin.CountedDays;
import com.nayeem.habittracker.checkin.HabitProgressService;
import com.nayeem.habittracker.checkin.StatsCalculator;
import com.nayeem.habittracker.checkin.Streak;
import com.nayeem.habittracker.checkin.StreakCalculator;
import com.nayeem.habittracker.checkin.WindowStats;
import com.nayeem.habittracker.dashboard.DashboardResponse.Completion;
import com.nayeem.habittracker.dashboard.DashboardResponse.GoalSummary;
import com.nayeem.habittracker.dashboard.DashboardResponse.HabitCompletion;
import com.nayeem.habittracker.dashboard.DashboardResponse.Today;
import com.nayeem.habittracker.dashboard.DashboardResponse.TodayHabit;
import com.nayeem.habittracker.dashboard.PatternsResponse.WeekdayRate;
import com.nayeem.habittracker.goal.Goal;
import com.nayeem.habittracker.goal.GoalProgressService;
import com.nayeem.habittracker.goal.GoalService;
import com.nayeem.habittracker.habit.DueRules;
import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitKind;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.level.LevelService;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The home screen, computed on read from logs in the user's timezone (PLAN.md §11.4). A handful of
 * queries whatever the number of habits (plus a few per active goal); the numbers come from the same
 * calculators as each habit's streak and stats, each goal's progress and the level, so the dashboard
 * can't disagree with those pages.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int[] WINDOWS = {7, 30, 90};

    private final HabitService habitService;
    private final HabitProgressService habitProgressService;
    private final GoalService goalService;
    private final GoalProgressService goalProgressService;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long userId) {
        ZoneId zone = ZoneId.of(userService.getById(userId).getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Habit> allHabits = habitService.findAllOwned(userId);
        List<Habit> habits = allHabits.stream()
                .filter(habit -> !habit.isArchived())
                .sorted(Comparator.comparing(Habit::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Habit::getId))
                .toList();
        Map<Long, Set<LocalDate>> doneDays = habitProgressService.doneDaysByHabit(userId);
        Map<Long, Integer> counts = habitProgressService.countsOn(userId, today);

        List<TodayHabit> todayHabits = new ArrayList<>();
        List<HabitCompletion> completions = new ArrayList<>();
        List<List<WindowStats>> current = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        List<List<WindowStats>> previous = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (Habit habit : habits) {
            LocalDate start = habit.startDay(zone);
            Set<LocalDate> done = CountedDays.of(habit.getKind(), doneDays.getOrDefault(habit.getId(), Set.of()),
                    start, today);
            todayHabits.add(todayHabit(habit, done, start, today, counts.getOrDefault(habit.getId(), 0)));

            var periods = new DashboardResponse.Period[WINDOWS.length];
            for (int i = 0; i < WINDOWS.length; i++) {
                WindowStats now = StatsCalculator.window(habit.getFrequencyType(), habit.getFrequencyConfig(), done,
                        start, today, WINDOWS[i]);
                WindowStats before = StatsCalculator.previousWindow(habit.getFrequencyType(),
                        habit.getFrequencyConfig(), done, start, today, WINDOWS[i]);
                current.get(i).add(now);
                previous.get(i).add(before);
                periods[i] = CompletionCalculator.period(now, before);
            }
            completions.add(new HabitCompletion(habit.getId(), habit.getName(), periods[0], periods[1], periods[2]));
        }

        todayHabits.sort(Comparator.comparing(TodayHabit::due).reversed());   // stable: names stay in order
        int due = (int) todayHabits.stream().filter(TodayHabit::due).count();
        int doneToday = (int) todayHabits.stream().filter(h -> h.due() && h.done()).count();
        List<GoalSummary> goals = new ArrayList<>();
        for (Goal goal : goalService.findActive(userId)) {
            goals.add(new GoalSummary(goal.getId(), goal.getTitle(), goal.getTargetDate(),
                    goalProgressService.progress(userId, goal.getId()).percent()));
        }
        return new DashboardResponse(
                new Today(today, due, doneToday, todayHabits),
                new Completion(
                        CompletionCalculator.overall(WINDOWS[0], current.get(0), previous.get(0)),
                        CompletionCalculator.overall(WINDOWS[1], current.get(1), previous.get(1)),
                        CompletionCalculator.overall(WINDOWS[2], current.get(2), previous.get(2)),
                        completions),
                HighlightCalculator.atRisk(todayHabits, today),
                HighlightCalculator.best(completions),
                HighlightCalculator.slipping(completions),
                goals,
                LevelService.levelOf(allHabits, doneDays, zone, today, goalService.countAchieved(userId)));
    }

    /**
     * Heatmap, weekday and time-of-day patterns over active habits — a separate call, because they
     * change slowly while the dashboard reloads after every check-in. Four queries.
     */
    @Transactional(readOnly = true)
    public PatternsResponse patterns(Long userId) {
        ZoneId zone = ZoneId.of(userService.getById(userId).getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Map<Long, Set<LocalDate>> doneDays = habitProgressService.doneDaysByHabit(userId);
        // quit habits have no schedule to keep; their clean days would only flatter the patterns
        List<Habit> active = habitService.findAllOwned(userId).stream()
                .filter(habit -> !habit.isArchived() && habit.getKind() == HabitKind.BUILD).toList();
        List<HabitDays> habits = active.stream()
                .map(habit -> new HabitDays(habit.getFrequencyType(), habit.getFrequencyConfig(),
                        habit.getCreatedAt().atZone(zone).toLocalDate(), doneDays.getOrDefault(habit.getId(), Set.of())))
                .toList();
        Set<Long> activeIds = active.stream().map(Habit::getId).collect(Collectors.toSet());
        List<CheckInTime> checkIns = habitProgressService
                .doneCheckInTimes(userId, today.minusDays(PatternCalculator.HOURS_DAYS - 1L)).stream()
                .filter(checkIn -> activeIds.contains(checkIn.habitId()))
                .toList();

        List<WeekdayRate> weekdays = PatternCalculator.weekdays(habits, today);
        int[] hours = PatternCalculator.hours(checkIns, zone);
        return new PatternsResponse(PatternCalculator.heatmap(habits, today), weekdays,
                PatternCalculator.weakest(weekdays), PatternCalculator.strongest(weekdays),
                PatternCalculator.asList(hours), PatternCalculator.peakHour(hours));
    }

    private static TodayHabit todayHabit(Habit habit, Set<LocalDate> done, LocalDate start, LocalDate today,
                                         int completedCount) {
        boolean doneToday = done.contains(today);
        Integer doneThisWeek = null;
        Integer timesPerWeek = null;
        long doneBeforeToday = 0;
        if (habit.getFrequencyType() == FrequencyType.X_TIMES_PER_WEEK) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            doneBeforeToday = done.stream().filter(d -> !d.isBefore(monday) && d.isBefore(today)).count();
            doneThisWeek = (int) doneBeforeToday + (doneToday ? 1 : 0);
            timesPerWeek = habit.getFrequencyConfig().timesPerWeek();
        }
        // a quit habit is never "to do"; done = clean so far today
        boolean due = habit.getKind() == HabitKind.BUILD
                && DueRules.isDue(habit.getFrequencyType(), habit.getFrequencyConfig(), today, doneBeforeToday);
        Streak streak = StreakCalculator.calculate(habit.getFrequencyType(), habit.getFrequencyConfig(), done, start,
                CountedDays.streakToday(habit.getKind(), today));
        return new TodayHabit(habit.getId(), habit.getName(), habit.getCategory(), habit.getKind(),
                habit.getFrequencyType(), habit.getTargetCount(), habit.getUnit(), completedCount, doneToday, due,
                doneThisWeek, timesPerWeek, streak.current(), streak.unit());
    }
}
