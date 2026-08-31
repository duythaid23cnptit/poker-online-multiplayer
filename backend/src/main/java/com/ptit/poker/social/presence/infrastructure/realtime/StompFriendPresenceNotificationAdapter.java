package com.ptit.poker.social.presence.infrastructure.realtime;

import com.ptit.poker.social.presence.api.realtime.FriendPresenceNotificationEvent;
import com.ptit.poker.social.presence.application.FriendPresenceNotificationPort;
import com.ptit.poker.social.presence.application.PresenceChanged;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
@Profile("!bootstrap")
class StompFriendPresenceNotificationAdapter implements FriendPresenceNotificationPort {
    private final SimpMessagingTemplate messaging;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    StompFriendPresenceNotificationAdapter(SimpMessagingTemplate messaging, JdbcTemplate jdbc, Clock clock) {
        this.messaging = messaging; this.jdbc = jdbc; this.clock = clock;
    }

    @Override
    public void notifyFriend(long recipientUserId, PresenceChanged changed) {
        String username = jdbc.queryForObject("SELECT username FROM users WHERE id=?", String.class, recipientUserId);
        FriendPresenceNotificationEvent event = new FriendPresenceNotificationEvent(
                1, UUID.randomUUID(), FriendPresenceNotificationEvent.EventType.FRIEND_STATUS_CHANGED,
                clock.instant(), new FriendPresenceNotificationEvent.Payload(changed.userId(), changed.presenceStatus()));
        messaging.convertAndSendToUser(username, "/queue/notifications", event);
    }
}
