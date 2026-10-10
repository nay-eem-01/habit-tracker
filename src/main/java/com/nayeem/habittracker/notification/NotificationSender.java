package com.nayeem.habittracker.notification;

/** Sends account email: password reset, email verification (PLAN.md §3.6). Reminders are never emailed. */
public interface NotificationSender {

    void send(OutgoingNotification notification);
}
