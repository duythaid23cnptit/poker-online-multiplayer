package com.ptit.poker.social.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!bootstrap")
class FriendshipNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendshipNotificationListener.class);
    private final SocialNotificationPort notifications;

    FriendshipNotificationListener(SocialNotificationPort notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void afterCommit(FriendshipNotificationRequested notification) {
        try {
            notifications.send(notification);
        } catch (RuntimeException failure) {
            LOGGER.error("Best-effort friendship notification failed type={} recipient={}",
                    notification.type(), notification.recipientUserId(), failure);
        }
    }
}
