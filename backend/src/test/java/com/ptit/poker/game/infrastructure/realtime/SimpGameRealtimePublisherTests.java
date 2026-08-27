package com.ptit.poker.game.infrastructure.realtime;

import static org.mockito.Mockito.*;
import com.ptit.poker.auth.domain.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import com.ptit.poker.game.api.realtime.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class SimpGameRealtimePublisherTests {
    @Test void routesPublicByGameAndPrivateOnlyByServerResolvedUsername() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class); UserRepository users = mock(UserRepository.class);
        UserEntity user = new UserEntity("alice", "hash", "alice@example.test", Role.PLAYER, AccountStatus.ACTIVE, 1_000);
        when(users.findById(7L)).thenReturn(java.util.Optional.of(user));
        var publisher = new SimpGameRealtimePublisher(messaging, users);
        UUID game = UUID.randomUUID();
        var event = new GameRealtimeEvent(UUID.randomUUID(), GameEventType.HOLE_CARDS, Instant.EPOCH, 3, game, 4, "safe");
        publisher.publishPublic(event); publisher.publishPrivate(7, event);
        verify(messaging).convertAndSend("/topic/game/" + game, event);
        verify(messaging).convertAndSendToUser("alice", "/queue/private", event);
        verifyNoMoreInteractions(messaging);
    }
}
