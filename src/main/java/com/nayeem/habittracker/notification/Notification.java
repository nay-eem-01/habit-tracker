package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.common.AuditModel;
import com.nayeem.habittracker.habit.Habit;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Something the app tells a user (plan §12.2): in-app first, other channels later.
 * {@code (habit, forDate, type)} is unique, so a reminder can't be created twice for the same
 * habit and day — after a restart, or with two app instances running the scheduler.
 */
@Getter
@Setter
@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_notifications_habit_date_type",
                columnNames = {"habit_id", "for_date", "type"}))
public class Notification extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** The habit this is about; null for notifications that aren't about one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", updatable = false)
    private Habit habit;

    /** The user's calendar day the notification is for (with the habit, makes it unique). */
    @Column(name = "for_date")
    private LocalDate forDate;

    /** Null while unread. */
    private Instant readAt;
}
