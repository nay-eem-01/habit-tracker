package com.nayeem.habittracker.habit;

import com.nayeem.habittracker.common.AuditModel;
import com.nayeem.habittracker.goal.Goal;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Something a user wants to do regularly (plan §2.2). Soft-deleted by archiving; the logs and
 * streak history stay. {@code User} has no {@code habits} collection: nothing needs to walk from a
 * user to all their habits in memory, and queries do it better.
 */
@Getter
@Setter
@Entity
@Table(name = "habits", indexes = {
        @Index(name = "idx_habits_user_id", columnList = "user_id"),
        @Index(name = "idx_habits_goal_id", columnList = "goal_id")})
public class Habit extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 50)
    private String category;

    /** Set together with the config through {@link #schedule}, so the two always agree. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FrequencyType frequencyType;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private FrequencyConfig frequencyConfig;

    /** Completions a day needs to count as done, e.g. 8 for "drink water 8×". */
    @Column(nullable = false)
    private int targetCount = 1;

    /** Local time of day, in the owner's timezone, to remind about the habit; null = no reminder. */
    private LocalTime reminderTime;

    @Column(nullable = false)
    private boolean archived;

    /** The goal this habit works towards (PLAN.md §11.1, one goal per habit); set only through {@link #linkToGoal}. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    /** Done days that make the habit "built" for its goal, e.g. 60. Null exactly when there is no goal. */
    @Setter(AccessLevel.NONE)
    private Integer goalTargetDays;

    /** The owner's day the habit was linked; goal progress counts done days from then on. */
    @Setter(AccessLevel.NONE)
    private LocalDate goalLinkedOn;

    /** Sets how often the habit is due; rejects a config that doesn't fit the type (400). */
    public void schedule(FrequencyType type, FrequencyConfig config) {
        this.frequencyConfig = FrequencyConfig.normalize(type, config);
        this.frequencyType = type;
    }

    /**
     * Links the habit to a goal. Re-linking to the same goal only changes the target; a different
     * goal starts counting afresh from {@code today}.
     */
    public void linkToGoal(Goal newGoal, int targetDays, LocalDate today) {
        if (goal == null || !goal.getId().equals(newGoal.getId())) {
            this.goalLinkedOn = today;
        }
        this.goal = newGoal;
        this.goalTargetDays = targetDays;
    }

    public void unlinkFromGoal() {
        this.goal = null;
        this.goalTargetDays = null;
        this.goalLinkedOn = null;
    }
}
