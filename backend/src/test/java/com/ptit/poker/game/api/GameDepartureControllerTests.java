package com.ptit.poker.game.api;

import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.runtime.GameRuntimeException;
import com.ptit.poker.game.application.runtime.GameRuntimeService;
import com.ptit.poker.game.application.runtime.GameRuntimeView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameDepartureControllerTests {
    private final GameRealtimeApplicationService games = mock(GameRealtimeApplicationService.class);
    private final GameDepartureController controller = new GameDepartureController(games);
    private final AuthenticatedUser user = new AuthenticatedUser(7L, "player", Role.PLAYER);
    private final UUID gameId = UUID.randomUUID();

    @Test
    void derivesIdentityFromPrincipalAndReturnsAuthoritativeDepartureState() {
        GameRuntimeView view = mock(GameRuntimeView.class);
        when(view.roomId()).thenReturn(12L);when(view.gameId()).thenReturn(gameId);
        when(games.requestDeparture(gameId, 7)).thenReturn(
                new GameRuntimeService.DepartureRequest(true, true, view));

        assertThat(controller.leave(user, gameId))
                .isEqualTo(new GameDepartureResponse(12, gameId, true, true));
        verify(games).requestDeparture(gameId, 7);
    }

    @Test
    void outsiderOrSpectatorIsForbidden() {
        when(games.requestDeparture(gameId, 7)).thenThrow(new GameRuntimeException("NOT_GAME_PARTICIPANT"));

        assertStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonPlayerAccountCannotEnterPlayerDepartureFlow() {
        AuthenticatedUser admin = new AuthenticatedUser(8L, "admin", Role.ADMIN);

        assertThatThrownBy(() -> controller.leave(admin, gameId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void unknownFinishedOrNonActiveGameIsAStableConflict() {
        when(games.requestDeparture(gameId, 7)).thenThrow(new GameRuntimeException("GAME_NOT_ACTIVE"));

        assertStatus(HttpStatus.CONFLICT);
    }

    private void assertStatus(HttpStatus expected) {
        assertThatThrownBy(() -> controller.leave(user, gameId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(expected));
    }
}
