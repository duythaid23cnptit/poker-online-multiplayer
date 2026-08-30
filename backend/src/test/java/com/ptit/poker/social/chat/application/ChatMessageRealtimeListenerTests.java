package com.ptit.poker.social.chat.application;

import com.ptit.poker.social.application.SocialPlayerQueryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatMessageRealtimeListenerTests {
    private final ChatRealtimePort realtime = mock(ChatRealtimePort.class);
    private final ChatMessageRealtimeListener listener = new ChatMessageRealtimeListener(realtime);

    @Test void committedMessageIsBroadcast() {
        ChatMessageCreated event = new ChatMessageCreated(message());
        listener.afterCommit(event);
        verify(realtime).broadcast(event.message());
    }

    @Test void broadcasterFailureIsContainedAfterCommit() {
        ChatMessageCreated event = new ChatMessageCreated(message());
        doThrow(new IllegalStateException("broker unavailable")).when(realtime).broadcast(event.message());
        assertThatCode(() -> listener.afterCommit(event)).doesNotThrowAnyException();
    }

    private static ChatMessageView message() {
        return new ChatMessageView(9, 4, UUID.randomUUID().toString(), "hello",
                Instant.parse("2026-08-30T01:00:00Z"),
                new SocialPlayerQueryPort.SafePlayerSummary(7, "Alpha", null));
    }
}
