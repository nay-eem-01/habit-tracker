package com.nayeem.habittracker.push;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends once the row that asked for the push is committed (a rolled-back run sends nothing), off the caller's thread. */
@Component
@RequiredArgsConstructor
class PushDispatcher {

    private final PushService pushService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCommitted(PushMessage message) {
        pushService.sendToUser(message.userId(), message.title(), message.body(), message.url());
    }
}
