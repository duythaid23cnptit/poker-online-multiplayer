package com.ptit.poker.game.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ptit.poker.game.application.runtime.GameRuntimeView;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.room.api.event.RealtimeEvent;
import com.ptit.poker.room.api.event.RoomEventType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class SimpRoomGameDiscoveryPublisherTests {
    @Test void publishesAuthoritativeIdentifiersThroughRoomEnvelope() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        UUID gameId = UUID.randomUUID();
        GameRuntimeView view = new GameRuntimeView(gameId, 22, 9, 31, 4, 1, 2, 3,
                GamePhase.PRE_FLOP, null, null, 7, 0, 20, 10, 20, List.of(), List.of(), false, false, false);
        new SimpRoomGameDiscoveryPublisher(messaging).publishStarted(view);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/room/9"), payload.capture());
        RealtimeEvent envelope = (RealtimeEvent) payload.getValue();
        assertThat(envelope.type()).isEqualTo(RoomEventType.GAME_STARTED);
        assertThat(envelope.scope().roomId()).isEqualTo(9);
        assertThat(envelope.payload()).isEqualTo(new SimpRoomGameDiscoveryPublisher.GameStartedDiscovery(gameId, 22, 31, 4));
    }
}
