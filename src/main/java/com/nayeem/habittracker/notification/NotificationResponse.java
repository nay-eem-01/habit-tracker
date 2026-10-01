package com.nayeem.habittracker.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        Long habitId,
        Instant readAt,
        Instant createdAt) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getHabit() == null ? null : n.getHabit().getId(), n.getReadAt(), n.getCreatedAt());
    }
}
