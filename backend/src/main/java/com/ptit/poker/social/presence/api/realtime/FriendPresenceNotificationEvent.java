package com.ptit.poker.social.presence.api.realtime;

import com.ptit.poker.player.domain.PresenceStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FriendPresenceNotificationEvent(int protocolVersion, UUID eventId, EventType type,
                                              Instant occurredAt, Payload payload) {
    public FriendPresenceNotificationEvent {
        if (protocolVersion != 1) throw new IllegalArgumentException("Unsupported protocol version");
        Objects.requireNonNull(eventId); Objects.requireNonNull(type);
        Objects.requireNonNull(occurredAt); Objects.requireNonNull(payload);
    }

    public enum EventType { FRIEND_STATUS_CHANGED }
    public record Payload(long userId, PresenceStatus presenceStatus) {
        public Payload { Objects.requireNonNull(presenceStatus); }
    }
}
