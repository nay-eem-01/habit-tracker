package com.nayeem.habittracker.habit;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.common.response.PageResponse;
import com.nayeem.habittracker.goal.Goal;
import com.nayeem.habittracker.goal.GoalService;
import com.nayeem.habittracker.goal.GoalStatus;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Habits of the signed-in user. Every method takes the acting user's id (from the security
 * context, never the request) and only ever sees that user's habits — anyone else's is 404.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;
    private final UserService userService;
    private final GoalService goalService;
    private final Clock clock;

    @Transactional
    public HabitResponse create(Long userId, HabitRequest request) {
        Habit habit = new Habit();
        habit.setUser(userService.getById(userId));
        apply(habit, request);
        habit = habitRepository.save(habit);
        log.info("Habit {} created by user {}", habit.getId(), userId);
        return HabitResponse.from(habit);
    }

    @Transactional(readOnly = true)
    public HabitResponse get(Long userId, Long habitId) {
        return HabitResponse.from(find(userId, habitId));
    }

    @Transactional(readOnly = true)
    public PageResponse<HabitResponse> list(Long userId, boolean archived, Pageable pageable) {
        return PageResponse.from(habitRepository.findAllByUserIdAndArchived(userId, archived, pageable),
                HabitResponse::from);
    }

    /** Full replace: every field of the request, as on create. */
    @Transactional
    public HabitResponse update(Long userId, Long habitId, HabitRequest request) {
        Habit habit = find(userId, habitId);
        apply(habit, request);
        return HabitResponse.from(habitRepository.saveAndFlush(habit));
    }

    /**
     * Archive is the only delete (plan §2.2): the habit leaves the default list, its logs and
     * history stay. Archiving an archived habit is a no-op, not an error.
     */
    @Transactional
    public HabitResponse setArchived(Long userId, Long habitId, boolean archived) {
        Habit habit = find(userId, habitId);
        if (habit.isArchived() != archived) {
            habit.setArchived(archived);
            habit = habitRepository.saveAndFlush(habit);
            log.info("Habit {} {} by user {}", habitId, archived ? "archived" : "unarchived", userId);
        }
        return HabitResponse.from(habit);
    }

    /**
     * Links the habit to one of the user's active goals (or changes the target of an existing
     * link). Archived habits can't be linked; unarchive first.
     */
    @Transactional
    public HabitResponse linkGoal(Long userId, Long habitId, GoalLinkRequest request) {
        Habit habit = find(userId, habitId);
        if (habit.isArchived()) {
            throw new ApplicationException(ErrorCode.HABIT_ARCHIVED);
        }
        Goal goal = goalService.getOwnedGoal(userId, request.getGoalId());
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.GOAL_NOT_ACTIVE);
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(habit.getUser().getTimezone())));
        habit.linkToGoal(goal, request.getGoalTargetDays(), today);
        habit = habitRepository.saveAndFlush(habit);
        log.info("Habit {} linked to goal {} by user {}", habitId, goal.getId(), userId);
        return HabitResponse.from(habit);
    }

    /** Takes the habit off its goal; with no link it is a no-op, not an error. */
    @Transactional
    public HabitResponse unlinkGoal(Long userId, Long habitId) {
        Habit habit = find(userId, habitId);
        if (habit.getGoal() != null) {
            habit.unlinkFromGoal();
            habit = habitRepository.saveAndFlush(habit);
            log.info("Habit {} unlinked from its goal by user {}", habitId, userId);
        }
        return HabitResponse.from(habit);
    }

    /** Every habit of the user, archived ones included (what they earned stays — levels, M4). */
    @Transactional(readOnly = true)
    public List<Habit> findAllOwned(Long userId) {
        return habitRepository.findAllByUserId(userId);
    }

    /** The user's habits linked to the goal, archived ones included, oldest first. */
    @Transactional(readOnly = true)
    public List<Habit> findLinkedToGoal(Long userId, Long goalId) {
        return habitRepository.findAllByGoalIdAndUserIdOrderById(goalId, userId);
    }

    /**
     * Habits to remind about at this minute (their owner's local time), not yet done today. Call
     * inside a transaction: the lazy {@code user} is needed to tell the owner's day.
     */
    public List<Habit> findRemindableAt(Instant now) {
        return habitRepository.findRemindableAt(now);
    }

    /**
     * The user's habit, for other features that work on it (check-ins); 404 when it isn't theirs.
     * Call inside a transaction if you need its lazy {@code user}.
     */
    public Habit getOwnedHabit(Long userId, Long habitId) {
        return find(userId, habitId);
    }

    Habit find(Long userId, Long habitId) {
        return habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.HABIT_NOT_FOUND));
    }

    static void apply(Habit habit, HabitRequest request) {
        habit.setName(request.getName().trim());
        habit.setCategory(request.getCategory() == null || request.getCategory().isBlank()
                ? null : request.getCategory().trim());
        habit.schedule(request.getFrequencyType(), request.getFrequencyConfig());
        habit.setTargetCount(request.getTargetCount() == null ? 1 : request.getTargetCount());
        habit.setReminderTime(request.getReminderTime());
    }
}
