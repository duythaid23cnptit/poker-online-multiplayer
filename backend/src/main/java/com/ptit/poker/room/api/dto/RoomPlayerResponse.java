package com.ptit.poker.room.api.dto;

import com.ptit.poker.room.domain.RoomPlayerState;

public record RoomPlayerResponse(Long userId, String username, Integer seatNumber,
                                 RoomPlayerState state, long tableChips) {
}
