package com.ptit.poker.social.api.realtime;

import com.ptit.poker.social.application.FriendshipNotificationType;
import com.ptit.poker.social.application.SocialPlayerQueryPort;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SocialNotificationEvent(
        int protocolVersion,
        UUID eventId,
        FriendshipNotificationType type,
        Instant occurredAt,
        Payload payload) {

    public SocialNotificationEvent {
        if (protocolVersion != 1) throw new IllegalArgumentException("Unsupported protocol version");
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(payload);
    }

    public record Payload(Long requestId, SocialPlayerQueryPort.SafePlayerSummary player) {
        public Payload { Objects.requireNonNull(player); }
    }
}
