package com.ptit.poker.social.chat.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!bootstrap")
class ChatMessageRealtimeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageRealtimeListener.class);
    private final ChatRealtimePort realtime;

    ChatMessageRealtimeListener(ChatRealtimePort realtime) { this.realtime = realtime; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void afterCommit(ChatMessageCreated created) {
        try {
            realtime.broadcast(created.message());
        } catch (RuntimeException failure) {
            LOGGER.error("Best-effort chat broadcast failed room={} message={}",
                    created.message().roomId(), created.message().messageId(), failure);
        }
    }
}
