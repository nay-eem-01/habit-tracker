package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.checkin.HabitProgressService;
import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Turns "this habit's reminder time is now" into a notification (plan §12.2). Minutes missed
 * while the app was down are not made up. Logs ids only — never habit names or emails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final HabitService habitService;
    private final HabitProgressService habitProgressService;
    private final NotificationRepository notificationRepository;

    /** @return how many reminders were created for the minute containing {@code now} */
    @Transactional
    public int sendDue(Instant now) {
        int created = 0;
        for (Habit habit : habitService.findRemindableAt(now)) {
            LocalDate today = LocalDate.ofInstant(now, ZoneId.of(habit.getUser().getTimezone()));
            if (!isDue(habit, today)) {
                continue;
            }
            int inserted = notificationRepository.insertIfAbsent(habit.getUser().getId(), habit.getId(),
                    NotificationType.HABIT_REMINDER.name(), "Reminder: " + habit.getName(),
                    "Time to do it. Check in to keep your streak going.", today, now);
            if (inserted == 1) {
                created++;
                log.info("Reminder created for habit {} (user {}) on {}", habit.getId(), habit.getUser().getId(), today);
            }
        }
        return created;
    }

    private boolean isDue(Habit habit, LocalDate today) {
        long doneThisWeek = 0;
        if (habit.getFrequencyType() == FrequencyType.X_TIMES_PER_WEEK) {
            LocalDate monday = today.with(DayOfWeek.MONDAY);
            doneThisWeek = habitProgressService.doneDays(habit.getId(), monday, monday.plusDays(6));
        }
        return ReminderRules.isDue(habit.getFrequencyType(), habit.getFrequencyConfig(), today, doneThisWeek);
    }
}
