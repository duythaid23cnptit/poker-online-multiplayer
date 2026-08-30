package com.ptit.poker.social.chat.api.realtime;

import com.ptit.poker.social.chat.application.ChatMessageView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChatRealtimeEvent(int protocolVersion, UUID eventId, ChatEventType type,
                                Instant occurredAt, Scope scope, ChatMessageView payload) {
    public ChatRealtimeEvent {
        if (protocolVersion != 1) throw new IllegalArgumentException("Unsupported protocol version");
        Objects.requireNonNull(eventId); Objects.requireNonNull(type); Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(scope); Objects.requireNonNull(payload);
    }

    public enum ChatEventType { CHAT_MESSAGE }
    public record Scope(long roomId) { }
}
