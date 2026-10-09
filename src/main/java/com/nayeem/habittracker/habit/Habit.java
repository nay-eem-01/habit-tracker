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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    /** Set at creation, never changed: it decides what the logs mean. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private HabitKind kind = HabitKind.BUILD;

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

    /** The owner's day the current schedule started; null = the day the habit was created. */
    @Setter(AccessLevel.NONE)
    private LocalDate scheduleSince;

    /** Schedules before the current one, oldest first — the XP earned under them is kept. */
    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<PastSchedule> pastSchedules = new ArrayList<>();

    /** Completions a day needs to count as done, e.g. 8 for "drink water 8×". */
    @Column(nullable = false)
    private int targetCount = 1;

    /** What a counted habit counts ("glasses"); null = no unit. */
    @Column(length = 20)
    private String unit;

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
     * Call before {@link #schedule} on an update: if the schedule is really changing, the current one
     * is kept as a {@link PastSchedule} and the new one starts {@code today} — with a new streak, so
     * the past isn't re-judged by rules it wasn't kept under.
     */
    public void startNewScheduleIfChanged(FrequencyType type, FrequencyConfig config, LocalDate today, ZoneId zone) {
        if (type == frequencyType && Objects.equals(FrequencyConfig.normalize(type, config), frequencyConfig)) {
            return;
        }
        LocalDate start = startDay(zone);
        if (start.isBefore(today)) {
            List<PastSchedule> past = new ArrayList<>(pastSchedules);
            past.add(new PastSchedule(frequencyType, frequencyConfig, start, today.minusDays(1)));
            pastSchedules = past;
        }
        scheduleSince = today;
    }

    /** For a new habit only; a habit's kind never changes. */
    public void startAs(HabitKind kind) {
        this.kind = kind;
    }

    /** The owner's first day of the current schedule: where streaks, stats and current XP start. */
    public LocalDate startDay(ZoneId zone) {
        return scheduleSince != null ? scheduleSince : getCreatedAt().atZone(zone).toLocalDate();
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
