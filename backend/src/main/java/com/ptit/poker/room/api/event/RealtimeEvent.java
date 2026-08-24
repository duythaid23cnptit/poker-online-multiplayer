package com.ptit.poker.room.api.event;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEvent(int protocolVersion, UUID eventId, RoomEventType type, Instant occurredAt,
                            Scope scope, Object payload) {
    public record Scope(Long roomId) {}

    public static RealtimeEvent room(RoomEventType type, Long roomId, Object payload) {
        return new RealtimeEvent(1, UUID.randomUUID(), type, Instant.now(), new Scope(roomId), payload);
    }
}
