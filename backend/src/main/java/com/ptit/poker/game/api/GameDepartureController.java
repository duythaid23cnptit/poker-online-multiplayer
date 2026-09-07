package com.ptit.poker.game.api;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.runtime.GameRuntimeException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/games")
public class GameDepartureController {
    private final GameRealtimeApplicationService games;

    public GameDepartureController(GameRealtimeApplicationService games) {
        this.games = games;
    }

    @PostMapping("/{gameId}/leave")
    GameDepartureResponse leave(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID gameId) {
        if (user.role() != Role.PLAYER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PLAYER_ACCOUNT_REQUIRED");
        }
        try {
            var departure = games.requestDeparture(gameId, user.userId());
            return new GameDepartureResponse(departure.view().roomId(), departure.view().gameId(),
                    departure.changed(), departure.deferred());
        } catch (GameRuntimeException failure) {
            HttpStatus status = "NOT_GAME_PARTICIPANT".equals(failure.code())
                    ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
            throw new ResponseStatusException(status, failure.code(), failure);
        }
    }
}
