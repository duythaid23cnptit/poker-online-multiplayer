package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.game.api.realtime.GameRealtimeEvent;
import com.ptit.poker.game.application.realtime.GameRealtimePublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component @Profile("!bootstrap")
public class SimpGameRealtimePublisher implements GameRealtimePublisher {
    private final SimpMessagingTemplate messaging;
    private final UserRepository users;
    public SimpGameRealtimePublisher(SimpMessagingTemplate messaging, UserRepository users) {
        this.messaging = messaging; this.users = users;
    }
    @Override public void publishPublic(GameRealtimeEvent event) {
        messaging.convertAndSend("/topic/game/" + event.gameId(), event);
    }
    @Override public void publishPrivate(long userId, GameRealtimeEvent event) {
        String username = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found")).getUsername();
        messaging.convertAndSendToUser(username, "/queue/private", event);
    }
}
