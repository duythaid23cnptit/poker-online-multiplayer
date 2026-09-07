package com.ptit.poker.game.api;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.game.application.GameSnapshotQueryService;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/games")
public class GameQueryController {
    private final GameSnapshotQueryService games;

    public GameQueryController(GameSnapshotQueryService games) { this.games = games; }

    @GetMapping("/{gameId}/snapshot")
    GameSnapshotResponse snapshot(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID gameId) {
        GameSnapshotResponse snapshot = games.snapshot(gameId, user.userId());
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Game access forbidden");
        return snapshot;
    }

    @GetMapping("/active/room/{roomId}")
    ActiveGameResponse active(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long roomId) {
        ActiveGameResponse active = games.activeGame(roomId, user.userId());
        if (active == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No observable active game");
        return active;
    }

    @GetMapping("/active/me")
    ActiveGameResponse activeMine(@AuthenticationPrincipal AuthenticatedUser user) {
        ActiveGameResponse active = games.activeGameForUser(user.userId());
        if (active == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active game");
        return active;
    }
}
