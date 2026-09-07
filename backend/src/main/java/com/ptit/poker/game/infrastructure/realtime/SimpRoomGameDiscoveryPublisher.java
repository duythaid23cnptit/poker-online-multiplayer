package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.game.application.realtime.RoomGameDiscoveryPublisher;
import com.ptit.poker.game.application.runtime.GameRuntimeView;
import com.ptit.poker.room.api.event.RealtimeEvent;
import com.ptit.poker.room.api.event.RoomEventType;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class SimpRoomGameDiscoveryPublisher implements RoomGameDiscoveryPublisher {
    private final SimpMessagingTemplate messaging;

    public SimpRoomGameDiscoveryPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publishStarted(GameRuntimeView view) {
        messaging.convertAndSend("/topic/room/" + view.roomId(), RealtimeEvent.room(
                RoomEventType.GAME_STARTED,
                view.roomId(),
                new GameStartedDiscovery(view.gameId(), view.gameSessionId(), view.handId(), view.handNumber())));
    }

    public record GameStartedDiscovery(UUID gameId, long gameSessionId, long handId, long handNumber) {}
}
