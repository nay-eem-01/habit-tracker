package com.nayeem.habittracker.notification;

/** A delivery channel beyond the in-app list (plan §12.2). Push joins here once there is a frontend. */
public interface NotificationSender {

    void send(OutgoingNotification notification);
}
