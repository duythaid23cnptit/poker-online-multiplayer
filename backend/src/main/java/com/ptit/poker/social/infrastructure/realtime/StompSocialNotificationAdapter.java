package com.ptit.poker.social.infrastructure.realtime;

import com.ptit.poker.social.api.realtime.SocialNotificationEvent;
import com.ptit.poker.social.application.FriendshipNotificationRequested;
import com.ptit.poker.social.application.SocialNotificationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!bootstrap")
class StompSocialNotificationAdapter implements SocialNotificationPort {
    private final SimpMessagingTemplate messaging;
    private final JdbcTemplate jdbc;

    StompSocialNotificationAdapter(SimpMessagingTemplate messaging, JdbcTemplate jdbc) {
        this.messaging = messaging;
        this.jdbc = jdbc;
    }

    @Override
    public void send(FriendshipNotificationRequested notification) {
        String username = jdbc.queryForObject(
                "SELECT username FROM users WHERE id=?", String.class, notification.recipientUserId());
        SocialNotificationEvent event = new SocialNotificationEvent(
                1, UUID.randomUUID(), notification.type(), notification.occurredAt(),
                new SocialNotificationEvent.Payload(notification.requestId(), notification.actor()));
        messaging.convertAndSendToUser(username, "/queue/notifications", event);
    }
}
