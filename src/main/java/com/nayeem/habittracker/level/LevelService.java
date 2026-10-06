package com.nayeem.habittracker.level;

import com.nayeem.habittracker.checkin.HabitProgressService;
import com.nayeem.habittracker.goal.GoalService;
import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
        Map<Long, Set<LocalDate>> doneDays = habitProgressService.doneDaysByHabit(userId);

        long xp = XpCalculator.goalXp(goalService.countAchieved(userId));
        for (Habit habit : habitService.findAllOwned(userId)) {
            xp += XpCalculator.habitXp(habit.getFrequencyType(), habit.getFrequencyConfig(),
                    doneDays.getOrDefault(habit.getId(), Set.of()), habit.getCreatedAt().atZone(zone).toLocalDate(),
                    today);
        }
        return Level.of(xp);
    }
}
