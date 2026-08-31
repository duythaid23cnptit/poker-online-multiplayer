package com.ptit.poker.social.presence.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!bootstrap")
class FriendPresenceNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendPresenceNotificationListener.class);
    private final AcceptedFriendQueryPort friends;
    private final FriendPresenceNotificationPort notifications;

    FriendPresenceNotificationListener(AcceptedFriendQueryPort friends, FriendPresenceNotificationPort notifications) {
        this.friends = friends; this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void afterCommit(PresenceChanged changed) {
        List<Long> friendIds;
        try {
            friendIds = friends.findAcceptedFriendIds(changed.userId());
        } catch (RuntimeException failure) {
            LOGGER.error("Best-effort friend presence recipient lookup failed user={}", changed.userId(), failure);
            return;
        }
        for (long friendId : friendIds) {
            try { notifications.notifyFriend(friendId, changed); }
            catch (RuntimeException failure) {
                LOGGER.error("Best-effort friend presence notification failed user={} recipient={}",
                        changed.userId(), friendId, failure);
            }
        }
    }
}
