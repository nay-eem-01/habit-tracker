package com.nayeem.habittracker.notification;

/**
 * An email on its way out, published as an event and sent after the transaction commits.
 * {@code toString} leaves the address out (it ends up in logs).
 */
public record OutgoingNotification(Long userId, String email, String title, String body) {

    @Override
    public String toString() {
        return "OutgoingNotification[userId=" + userId + "]";
    }
}
