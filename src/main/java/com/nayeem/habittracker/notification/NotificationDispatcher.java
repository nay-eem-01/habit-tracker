package com.nayeem.habittracker.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands an email to the channel once the transaction that asked for it has committed — a rolled-back
 * request sends nothing — and on another thread, so a slow mail server never delays the response.
 * A failing channel is logged and dropped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NotificationDispatcher {

    private final NotificationSender sender;

    @Async
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
