package com.ptit.poker.room.application;

import org.junit.jupiter.api.Test;
import com.ptit.poker.room.api.event.RoomEventType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RoomRealtimePublisherTests {
    @Test
    void publishesLobbyEventWithoutPersistenceEntities() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        RoomRealtimePublisher publisher = new RoomRealtimePublisher(messaging);
        publisher.publish(new RoomChangedEvent(12L, null, null, RoomEventType.ROOM_CREATED, "safe-payload"));
        verify(messaging).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/lobby"), any(Object.class));
    }

    @Test
    void roomOnlyChangeDoesNotSpamLobby() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        new RoomRealtimePublisher(messaging).publish(new RoomChangedEvent(
                12L, RoomEventType.PLAYER_READY, "safe", null, null));
        verify(messaging).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/room/12"), any(Object.class));
        verify(messaging, never()).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/lobby"), any(Object.class));
    }
}
