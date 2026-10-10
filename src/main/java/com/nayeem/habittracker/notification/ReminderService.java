package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.checkin.HabitProgressService;
import com.nayeem.habittracker.habit.DueRules;
import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.push.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Turns "this habit's reminder time is now" into an in-app notification and a web push — never an
 * email (PLAN.md §3.6). Minutes missed while the app was down are not made up. Logs ids only — never
 * habit names or emails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final HabitService habitService;
    private final HabitProgressService habitProgressService;
    private final NotificationRepository notificationRepository;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher events;

    /**
     * Each habit in its own transaction: one that fails is logged and skipped, the others still get
     * their reminder.
     *
     * @return how many reminders were created for the minute containing {@code now}
     */
    public int sendDue(Instant now) {
        int created = 0;
        for (Long habitId : habitService.findRemindableAt(now)) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> remind(habitId, now)))) {
                    created++;
                }
            } catch (RuntimeException e) {
                log.error("Reminder for habit {} failed", habitId, e);
            }
        }
        return created;
    }

    /** @return true if a new reminder was created */
    private boolean remind(Long habitId, Instant now) {
        Habit habit = habitService.getForSystem(habitId);
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of(habit.getUser().getTimezone()));
        if (!isDue(habit, today)) {
            return false;
        }
        String title = "Reminder: " + habit.getName();
        String body = "Time to do it. Check in to keep your streak going.";
        int inserted = notificationRepository.insertIfAbsent(habit.getUser().getId(), habit.getId(),
                NotificationType.HABIT_REMINDER.name(), title, body, today, now);
        if (inserted == 0) {
            return false;
        }
        events.publishEvent(new PushMessage(habit.getUser().getId(), title, body, "/"));   // sent after commit
        log.info("Reminder created for habit {} (user {}) on {}", habit.getId(), habit.getUser().getId(), today);
        return true;
    }

    private boolean isDue(Habit habit, LocalDate today) {
        long doneThisWeek = 0;
        if (habit.getFrequencyType() == FrequencyType.X_TIMES_PER_WEEK) {
            LocalDate monday = today.with(DayOfWeek.MONDAY);
            doneThisWeek = habitProgressService.doneDays(habit.getId(), monday, monday.plusDays(6));
        }
        return DueRules.isDue(habit.getFrequencyType(), habit.getFrequencyConfig(), today, doneThisWeek);
    }
}
