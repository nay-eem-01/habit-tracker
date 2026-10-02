package com.nayeem.habittracker.goal;

import com.nayeem.habittracker.checkin.HabitProgressService;
import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal progress, computed on read from habit logs — never stored (PLAN.md §1.3, §11.1). Lives apart
 * from {@link GoalService} because it needs habits and check-ins, which themselves need goals.
 */
@Service
@RequiredArgsConstructor
public class GoalProgressService {

    private final GoalService goalService;
    private final HabitService habitService;
    private final HabitProgressService habitProgressService;
    private final UserService userService;
    private final Clock clock;

    /**
     * Each linked habit's done days since it was linked, against its target; the goal is the average
     * over the habits that are still active. Archived habits are listed (what they earned stays
     * visible) but no longer count, so shelving a habit doesn't hold the goal back for ever.
     */
    @Transactional(readOnly = true)
    public GoalProgress progress(Long userId, Long goalId) {
        goalService.getOwnedGoal(userId, goalId);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(userService.getById(userId).getTimezone())));

        List<HabitGoalProgress> habits = new ArrayList<>();
        List<Double> counted = new ArrayList<>();
        for (Habit habit : habitService.findLinkedToGoal(userId, goalId)) {
            long done = habitProgressService.doneDays(habit.getId(), habit.getGoalLinkedOn(), today);
            double fraction = GoalProgressCalculator.habitFraction(done, habit.getGoalTargetDays());
            habits.add(new HabitGoalProgress(habit.getId(), habit.getName(), habit.isArchived(),
                    habit.getGoalLinkedOn(), done, habit.getGoalTargetDays(), GoalProgressCalculator.percent(fraction)));
            if (!habit.isArchived()) {
                counted.add(fraction);
            }
        }
        return new GoalProgress(goalId, GoalProgressCalculator.percent(GoalProgressCalculator.goalFraction(counted)), habits);
    }
}
