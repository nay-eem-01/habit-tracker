package com.nayeem.habittracker.goal;

import com.nayeem.habittracker.common.AuditModel;
import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What a user wants to reach; habits are the daily actions that get them there (PLAN.md §11.1).
 * Progress is never stored — it is computed from habit logs on read, like streaks.
 */
@Getter
@Setter
@Entity
@Table(name = "goals", indexes = @Index(name = "idx_goals_user_id", columnList = "user_id"))
public class Goal extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 2000)
    private String description;

    /** "By when", a calendar day in the owner's timezone; null = no deadline. */
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status = GoalStatus.ACTIVE;

    /** When the user marked it achieved; null otherwise. */
    private Instant achievedAt;

    /**
     * Moves an active goal to {@code ACHIEVED} or {@code ABANDONED}. Doing the same again is a no-op
     * (keeps the first {@code achievedAt}); switching between the two is refused (409).
     */
    public void close(GoalStatus outcome, Instant now) {
        if (outcome == GoalStatus.ACTIVE) {
            throw new IllegalArgumentException("A goal is closed as ACHIEVED or ABANDONED");
        }
        if (status == outcome) {
            return;
        }
        if (status != GoalStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.GOAL_ALREADY_CLOSED);
        }
        status = outcome;
        achievedAt = outcome == GoalStatus.ACHIEVED ? now : null;
    }
}
