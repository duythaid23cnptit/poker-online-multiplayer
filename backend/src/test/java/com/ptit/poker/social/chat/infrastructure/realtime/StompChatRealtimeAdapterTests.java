package com.ptit.poker.social.chat.infrastructure.realtime;

import com.ptit.poker.social.application.SocialPlayerQueryPort;
import com.ptit.poker.social.chat.api.realtime.ChatRealtimeEvent;
import com.ptit.poker.social.chat.application.ChatMessageView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StompChatRealtimeAdapterTests {
    private static final Instant NOW = Instant.parse("2026-08-30T02:00:00Z");

    @Test void broadcastsCanonicalEnvelopeOnlyToRoomTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompChatRealtimeAdapter adapter = new StompChatRealtimeAdapter(
                messaging, Clock.fixed(NOW, ZoneOffset.UTC));
        ChatMessageView message = message();

        adapter.broadcast(message);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/room/12"), payload.capture());
        ChatRealtimeEvent event = (ChatRealtimeEvent) payload.getValue();
        assertThat(event.protocolVersion()).isEqualTo(1);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.type()).isEqualTo(ChatRealtimeEvent.ChatEventType.CHAT_MESSAGE);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.scope().roomId()).isEqualTo(12);
        assertThat(event.payload()).isEqualTo(message);
    }

    @Test void eventIdIsIndependentFromClientMessageId() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        StompChatRealtimeAdapter adapter = new StompChatRealtimeAdapter(
                messaging, Clock.fixed(NOW, ZoneOffset.UTC));
        ChatMessageView message = message();
        adapter.broadcast(message);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/room/12"), payload.capture());
        assertThat(((ChatRealtimeEvent) payload.getValue()).eventId().toString())
                .isNotEqualTo(message.clientMessageId());
    }

    private static ChatMessageView message() {
        return new ChatMessageView(99, 12, UUID.randomUUID().toString(), "hello", NOW,
                new SocialPlayerQueryPort.SafePlayerSummary(7, "Alpha", null));
    }
}
