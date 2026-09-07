package com.ptit.poker.game.api;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.runtime.GameRuntimeException;
import com.ptit.poker.game.application.runtime.GameRuntimeView;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/games")
public class GameStartController {
    private final GameRealtimeApplicationService games;

    public GameStartController(GameRealtimeApplicationService games) {
        this.games = games;
    }

    @PostMapping("/rooms/{roomId}/start")
    ActiveGameResponse start(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long roomId) {
        try {
            GameRuntimeView started = games.startGame(roomId, user.userId());
            return new ActiveGameResponse(started.roomId(), started.gameId(), started.gameSessionId(),
                    started.handId(), started.handNumber());
        } catch (GameRuntimeException failure) {
            HttpStatus status = "ROOM_HOST_REQUIRED".equals(failure.code())
                    ? HttpStatus.FORBIDDEN
                    : "ROOM_NOT_FOUND".equals(failure.code()) ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            throw new ResponseStatusException(status, message(failure.code()), failure);
        }
    }

    private static String message(String code) {
        return switch (code) {
            case "ROOM_HOST_REQUIRED" -> "Only the room host can start the game.";
            case "INSUFFICIENT_PLAYERS" -> "At least 2 seated players are required.";
            case "PLAYERS_NOT_READY" -> "Every seated player must be ready.";
            case "ROOM_NOT_WAITING", "GAME_ALREADY_ACTIVE" -> "This room has already started.";
            case "ROOM_NOT_FOUND" -> "Room not found.";
            default -> "The game cannot be started right now.";
        };
    }
}
