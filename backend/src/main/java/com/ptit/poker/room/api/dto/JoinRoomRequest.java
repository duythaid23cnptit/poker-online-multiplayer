package com.ptit.poker.room.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
        Boolean spectator,
        @Min(1) Integer seatNumber,
        @Min(1) Long buyInAmount,
        @Size(max = 72) String password) {
    public boolean joinsAsSpectator() { return Boolean.TRUE.equals(spectator); }
}
