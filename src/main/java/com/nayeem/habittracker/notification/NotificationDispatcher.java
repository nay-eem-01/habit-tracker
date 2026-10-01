package com.nayeem.habittracker.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands a notification to the channel once its row is committed — so a slow mail server never
 * holds a transaction open, and a rolled-back run sends nothing. A failing channel is logged and
 * dropped: the in-app notification is already there, and a late reminder is worse than none.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NotificationDispatcher {

    private final NotificationSender sender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCommitted(OutgoingNotification notification) {
        try {
            sender.send(notification);
        } catch (RuntimeException e) {
            // class only: a mail exception's message can carry the address
            log.error("Delivery to user {} failed: {}", notification.userId(), e.getClass().getSimpleName());
        }
    }
}
