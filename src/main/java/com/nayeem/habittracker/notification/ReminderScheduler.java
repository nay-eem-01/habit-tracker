package com.nayeem.habittracker.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Every minute, on the minute. Switched off with {@code app.reminders.enabled=false} (tests call
 * {@link ReminderService} directly with a chosen instant). A failed run is logged and the next
 * minute carries on.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
class ReminderScheduler {

    private final ReminderService reminderService;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *")
    void run() {
        try {
            int created = reminderService.sendDue(clock.instant());
            if (created > 0) {
                log.debug("{} reminder(s) created", created);
            }
        } catch (RuntimeException e) {
            log.error("Reminder run failed", e);
        }
    }
}
