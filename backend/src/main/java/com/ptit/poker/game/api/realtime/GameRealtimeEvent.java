package com.ptit.poker.game.api.realtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GameRealtimeEvent(UUID eventId, GameEventType type, Instant timestamp, long roomId,
                                UUID gameId, long version, Object payload) {
    public GameRealtimeEvent {
        Objects.requireNonNull(eventId); Objects.requireNonNull(type); Objects.requireNonNull(timestamp);
        Objects.requireNonNull(gameId); Objects.requireNonNull(payload);
    }
}
