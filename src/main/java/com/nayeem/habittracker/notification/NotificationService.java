package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * The signed-in user's notifications. Every method takes the acting user's id and only sees that
 * user's rows — someone else's notification is 404.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, Pageable pageable) {
        return PageResponse.from(notificationRepository.findForUser(userId, pageable), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    /** Marking a read notification again keeps the first read time. */
    @Transactional
    public NotificationResponse markRead(Long userId, Long id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
            notification = notificationRepository.saveAndFlush(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public MarkedReadResponse markAllRead(Long userId) {
        List<Notification> unread = notificationRepository.findAllByUserIdAndReadAtIsNull(userId);
        unread.forEach(n -> n.setReadAt(clock.instant()));
        notificationRepository.saveAll(unread);
        return new MarkedReadResponse(unread.size());
    }
}
