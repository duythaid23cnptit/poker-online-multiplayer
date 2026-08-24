package com.ptit.poker.room.api.dto;

import java.util.List;

public record RoomDetailResponse(RoomSummaryResponse room, List<RoomPlayerResponse> members) {
}
