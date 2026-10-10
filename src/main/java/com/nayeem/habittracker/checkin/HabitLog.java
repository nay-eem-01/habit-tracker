package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.common.AuditModel;
import com.nayeem.habittracker.habit.Habit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One habit on one day (plan §2.3). At most one row per {@code (habit, day)} — check-ins are
 * upserts against that constraint. {@code createdAt} doubles as "when did they check in", for the
 * time-of-day pattern later (PLAN.md §11.4).
 */
@Getter
@Setter
@Entity
@Table(name = "habit_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_habit_logs_habit_date", columnNames = {"habit_id", "log_date"}))
public class HabitLog extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_id", nullable = false, updatable = false)
    private Habit habit;

    /** The user's calendar day, in their timezone. */
    @Column(name = "log_date", nullable = false, updatable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    private int completedCount;

    /** The habit's target when this day was logged; the day is judged by it, not today's target. */
    @Column(nullable = false)
    private int targetCount;

    @Column(length = 500)
    private String note;

    public boolean isDone() {
        return completedCount >= targetCount;
    }
}
