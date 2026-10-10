package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-side numbers computed from a habit's logs — never stored (plan §1.3). */
@Service
@RequiredArgsConstructor
public class HabitProgressService {

    private final HabitService habitService;
    private final HabitLogRepository habitLogRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Streak streak(Long userId, Long habitId) {
        Context c = load(userId, habitId);
        return StreakCalculator.calculate(c.habit.getFrequencyType(), c.habit.getFrequencyConfig(), c.doneDays,
                c.restDays, c.start, CountedDays.streakToday(c.habit.getKind(), c.today));
    }

    /** Completion rate over the last 7 and 30 days. */
    @Transactional(readOnly = true)
    public HabitStats stats(Long userId, Long habitId) {
        Context c = load(userId, habitId);
        return new HabitStats(window(c, 7), window(c, 30));
    }

    /** Days in {@code from..to} (inclusive) on which the habit reached its target. */
    @Transactional(readOnly = true)
    public long doneDays(Long habitId, LocalDate from, LocalDate to) {
        return habitLogRepository.countDoneDays(habitId, from, to);
    }

    /** Done days of each of the user's habits, by habit id; a habit with none is absent. */
    @Transactional(readOnly = true)
    public Map<Long, Set<LocalDate>> doneDaysByHabit(Long userId) {
        Map<Long, Set<LocalDate>> byHabit = new HashMap<>();
        for (HabitDoneDay row : habitLogRepository.findDoneDaysOfUser(userId)) {
            byHabit.computeIfAbsent(row.habitId(), id -> new HashSet<>()).add(row.day());
        }
        return byHabit;
    }

    /** Rest days of each of the user's habits, by habit id; a habit with none is absent. */
    @Transactional(readOnly = true)
    public Map<Long, Set<LocalDate>> restDaysByHabit(Long userId) {
        Map<Long, Set<LocalDate>> byHabit = new HashMap<>();
        for (HabitDoneDay row : habitLogRepository.findRestDaysOfUser(userId)) {
            byHabit.computeIfAbsent(row.habitId(), id -> new HashSet<>()).add(row.day());
        }
        return byHabit;
    }

    /** XP the user has spent on rest days that still stand. */
    @Transactional(readOnly = true)
    public long restXpSpent(Long userId) {
        return habitLogRepository.sumRestCostOfUser(userId);
    }

    /** The user's done days since {@code from}, with when each was first logged. */
    @Transactional(readOnly = true)
    public List<CheckInTime> doneCheckInTimes(Long userId, LocalDate from) {
        return habitLogRepository.findDoneCheckInTimes(userId, from);
    }

    /** Each of the user's habits checked in on {@code day} → that day's count; others are absent. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> countsOn(Long userId, LocalDate day) {
        Map<Long, Integer> counts = new HashMap<>();
        for (HabitDayCount row : habitLogRepository.findCountsOn(userId, day)) {
            counts.put(row.habitId(), row.completedCount());
        }
        return counts;
    }

    private static WindowStats window(Context c, int days) {
        return StatsCalculator.window(c.habit.getFrequencyType(), c.habit.getFrequencyConfig(), c.doneDays,
                c.restDays, c.start, c.today, days);
    }

    /** The habit plus the first day of its schedule, today and counted days — all in the owner's timezone. */
    private Context load(Long userId, Long habitId) {
        Habit habit = habitService.getOwnedHabit(userId, habitId);
        ZoneId zone = ZoneId.of(habit.getUser().getTimezone());
        LocalDate start = habit.startDay(zone);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Set<LocalDate> logged = new HashSet<>(habitLogRepository.findDoneDays(habit.getId()));
        return new Context(habit, start, today, CountedDays.of(habit.getKind(), logged, start, today),
                new HashSet<>(habitLogRepository.findRestDays(habit.getId())));
    }

    private record Context(Habit habit, LocalDate start, LocalDate today, Set<LocalDate> doneDays,
                           Set<LocalDate> restDays) {
    }
}
