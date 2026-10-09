package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** What a user may keep active (PLAN.md §1). Archived habits and closed goals don't count. */
@Getter
@RequiredArgsConstructor
public enum Plan {

    FREE(7, 2),
    PRO(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int maxActiveHabits;
    private final int maxActiveGoals;

    /** @throws ApplicationException 403 {@code PLAN_LIMIT_REACHED} when one more active habit is over the plan */
    public void checkRoomForHabit(long activeHabits) {
        if (activeHabits >= maxActiveHabits) {
            throw new ApplicationException(ErrorCode.PLAN_LIMIT_REACHED,
                    "The free plan keeps up to " + maxActiveHabits + " active habits; archive one first");
        }
    }

    /** @throws ApplicationException 403 {@code PLAN_LIMIT_REACHED} when one more active goal is over the plan */
    public void checkRoomForGoal(long activeGoals) {
        if (activeGoals >= maxActiveGoals) {
            throw new ApplicationException(ErrorCode.PLAN_LIMIT_REACHED,
                    "The free plan keeps up to " + maxActiveGoals + " active goals; close one first");
        }
    }
}
