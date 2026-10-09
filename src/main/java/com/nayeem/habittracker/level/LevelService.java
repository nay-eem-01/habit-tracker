package com.nayeem.habittracker.level;

import com.nayeem.habittracker.checkin.CountedDays;
import com.nayeem.habittracker.checkin.HabitProgressService;
import com.nayeem.habittracker.goal.GoalService;
import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.habit.PastSchedule;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The user's level, computed on every read from habit logs and goals — no XP column that can drift
 * (PLAN.md §11.3). Three queries whatever the number of habits.
 */
@Service
@RequiredArgsConstructor
public class LevelService {

    private final HabitService habitService;
    private final HabitProgressService habitProgressService;
    private final GoalService goalService;
    private final UserService userService;
    private final Clock clock;

    /** Archived habits count too: XP once earned is never lost (Q8). */
    @Transactional(readOnly = true)
    public Level level(Long userId) {
        ZoneId zone = ZoneId.of(userService.getById(userId).getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return levelOf(habitService.findAllOwned(userId), habitProgressService.doneDaysByHabit(userId), zone, today,
                goalService.countAchieved(userId));
    }

    /**
     * The level from data the caller already has (the dashboard loads the same habits and done days).
     *
     * @param habits every habit of the user, archived ones included
     */
    public static Level levelOf(List<Habit> habits, Map<Long, Set<LocalDate>> doneDays, ZoneId zone, LocalDate today,
                                long achievedGoals) {
        long xp = XpCalculator.goalXp(achievedGoals);
        for (Habit habit : habits) {
            LocalDate start = habit.startDay(zone);
            Set<LocalDate> done = CountedDays.of(habit.getKind(), doneDays.getOrDefault(habit.getId(), Set.of()),
                    start, today);
            xp += XpCalculator.habitXp(habit.getFrequencyType(), habit.getFrequencyConfig(), done, start,
                    CountedDays.streakToday(habit.getKind(), today));
            for (PastSchedule past : habit.getPastSchedules()) {   // XP earned under earlier schedules stays
                xp += XpCalculator.habitXp(past.type(), past.config(), done, past.from(), past.until());
            }
        }
        return Level.of(xp);
    }
}
