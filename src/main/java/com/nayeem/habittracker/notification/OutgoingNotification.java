package com.nayeem.habittracker.notification;

/**
 * A notification on its way to a channel other than the in-app list. Published after the
 * notification row is committed. {@code toString} leaves the address out (it ends up in logs).
 */
public record OutgoingNotification(Long userId, String email, String title, String body) {

    @Override
    public String toString() {
        return "OutgoingNotification[userId=" + userId + "]";
    }
}
