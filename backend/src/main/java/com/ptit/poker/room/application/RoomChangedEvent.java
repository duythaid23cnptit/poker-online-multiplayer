package com.ptit.poker.room.application;

import com.ptit.poker.room.api.event.RoomEventType;

public record RoomChangedEvent(Long roomId, RoomEventType roomType, Object roomPayload,
                               RoomEventType lobbyType, Object lobbyPayload) {
}
