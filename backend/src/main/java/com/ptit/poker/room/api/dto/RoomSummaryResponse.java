package com.ptit.poker.room.api.dto;

import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;

public record RoomSummaryResponse(Long id, String name, Long ownerUserId, RoomType roomType,
                                  boolean passwordRequired, RoomStatus status, long seatedPlayers,
                                  int maxPlayers, long smallBlind, long bigBlind, long buyIn) {
}
