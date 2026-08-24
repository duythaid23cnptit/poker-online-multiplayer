package com.ptit.poker.room.api;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.room.api.dto.*;
import com.ptit.poker.room.application.RoomApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomApplicationService rooms;

    public RoomController(RoomApplicationService rooms) { this.rooms = rooms; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoomDetailResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateRoomRequest request) {
        return rooms.create(user.userId(), request);
    }

    @GetMapping
    List<RoomSummaryResponse> list() { return rooms.list(); }

    @GetMapping("/{roomId}")
    RoomDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId) {
        return rooms.detail(user.userId(), roomId);
    }

    @PostMapping("/{roomId}/join")
    RoomDetailResponse join(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId,
                            @Valid @RequestBody JoinRoomRequest request) {
        return rooms.join(user.userId(), roomId, request);
    }

    @PostMapping("/{roomId}/leave")
    RoomDetailResponse leave(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId) {
        return rooms.leave(user.userId(), roomId);
    }
}
