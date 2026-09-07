package com.ptit.poker.game.api;

import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.runtime.GameRuntimeException;
import com.ptit.poker.game.application.runtime.GameRuntimeView;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameStartControllerTests {
    private final GameRealtimeApplicationService games = mock(GameRealtimeApplicationService.class);
    private final GameStartController controller = new GameStartController(games);
    private final AuthenticatedUser host = new AuthenticatedUser(7L, "host", Role.PLAYER);

    @Test
    void derivesTheHostIdentityAndReturnsTheAuthoritativeGame() {
        UUID gameId = UUID.randomUUID();
        GameRuntimeView view = mock(GameRuntimeView.class);
        when(view.roomId()).thenReturn(12L);
        when(view.gameId()).thenReturn(gameId);
        when(view.gameSessionId()).thenReturn(31L);
        when(view.handId()).thenReturn(41L);
        when(view.handNumber()).thenReturn(1L);
        when(games.startGame(12, 7)).thenReturn(view);

        assertThat(controller.start(host, 12))
                .isEqualTo(new ActiveGameResponse(12, gameId, 31, 41, 1));
        verify(games).startGame(12, 7);
    }

    @Test
    void rejectsANonHostAsForbidden() {
        when(games.startGame(12, 7)).thenThrow(new GameRuntimeException("ROOM_HOST_REQUIRED"));
        assertStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void reportsReadinessAndConcurrentDuplicateFailuresAsConflicts() {
        when(games.startGame(12, 7)).thenThrow(new GameRuntimeException("PLAYERS_NOT_READY"));
        assertStatus(HttpStatus.CONFLICT);
    }

    private void assertStatus(HttpStatus expected) {
        assertThatThrownBy(() -> controller.start(host, 12))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(expected));
    }
}
