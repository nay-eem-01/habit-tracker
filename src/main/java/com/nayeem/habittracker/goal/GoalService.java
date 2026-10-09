package com.nayeem.habittracker.goal;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.common.response.PageResponse;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Goals of the signed-in user. Every method takes the acting user's id (from the security
 * context, never the request) and only ever sees that user's goals — anyone else's is 404.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional
    public GoalResponse create(Long userId, GoalRequest request) {
        Goal goal = new Goal();
        goal.setUser(userService.getById(userId));
        apply(goal, request);
        goal = goalRepository.save(goal);
        log.info("Goal {} created by user {}", goal.getId(), userId);
        return GoalResponse.from(goal);
    }

    /** How many of the user's goals are achieved (each is worth XP, PLAN.md §11.3). */
    @Transactional(readOnly = true)
    public long countAchieved(Long userId) {
        return goalRepository.countByUserIdAndStatus(userId, GoalStatus.ACHIEVED);
    }

    /** The user's active goals, oldest first (the dashboard shows their progress). */
    @Transactional(readOnly = true)
    public List<Goal> findActive(Long userId) {
        return goalRepository.findAllByUserIdAndStatusOrderById(userId, GoalStatus.ACTIVE);
    }

    /** Every goal of the user, oldest first — for the data export. */
    @Transactional(readOnly = true)
    public List<GoalResponse> exportAll(Long userId) {
        return goalRepository.findAllByUserIdOrderById(userId).stream().map(GoalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long goalId) {
        return GoalResponse.from(find(userId, goalId));
    }

    /** All the user's goals, or only those with {@code status} when given. */
    @Transactional(readOnly = true)
    public PageResponse<GoalResponse> list(Long userId, GoalStatus status, Pageable pageable) {
        var goals = status == null
                ? goalRepository.findAllByUserId(userId, pageable)
                : goalRepository.findAllByUserIdAndStatus(userId, status, pageable);
        return PageResponse.from(goals, GoalResponse::from);
    }

    /** Full replace of title, description and target date; the status has its own steps (G.4). */
    @Transactional
    public GoalResponse update(Long userId, Long goalId, GoalRequest request) {
        Goal goal = find(userId, goalId);
        apply(goal, request);
        return GoalResponse.from(goalRepository.saveAndFlush(goal));
    }

    /**
     * The user decides a goal is {@code ACHIEVED} or {@code ABANDONED} — it is never set by progress
     * reaching 100 % (Q7). Linked habits stay linked. Repeating the same call is fine.
     */
    @Transactional
    public GoalResponse close(Long userId, Long goalId, GoalStatus outcome) {
        Goal goal = find(userId, goalId);
        GoalStatus before = goal.getStatus();
        goal.close(outcome, Instant.now(clock));
        if (before != goal.getStatus()) {
            goal = goalRepository.saveAndFlush(goal);
            log.info("Goal {} {} by user {}", goalId, outcome, userId);
        }
        return GoalResponse.from(goal);
    }

    /** The user's goal, for other features that work on it (linking habits); 404 when it isn't theirs. */
    public Goal getOwnedGoal(Long userId, Long goalId) {
        return find(userId, goalId);
    }

    Goal find(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GOAL_NOT_FOUND));
    }

    static void apply(Goal goal, GoalRequest request) {
        goal.setTitle(request.getTitle().trim());
        goal.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().trim());
        goal.setTargetDate(request.getTargetDate());
    }
}
