package com.ptit.poker.room.api.dto;

import com.ptit.poker.room.domain.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull RoomType roomType,
        @Min(6) @Max(9) int maxPlayers,
        @Min(1) long smallBlind,
        @Min(2) long bigBlind,
        @Min(1) long buyIn,
        @Size(min = 8, max = 72) String password) {
}
