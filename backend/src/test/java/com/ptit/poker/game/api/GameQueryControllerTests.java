package com.ptit.poker.game.api;

import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.game.api.realtime.GameEventPayloads.State;
import com.ptit.poker.game.application.GameSnapshotQueryService;
import com.ptit.poker.game.domain.state.GamePhase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameQueryControllerTests {
    private final GameSnapshotQueryService games = mock(GameSnapshotQueryService.class);
    private final GameQueryController controller = new GameQueryController(games);
    private final AuthenticatedUser participant = new AuthenticatedUser(7L, "participant", Role.PLAYER);
    private final UUID gameId = UUID.randomUUID();

    @Test
    void finishedParticipantSnapshotKeepsExistingSuccessfulControllerContract() {
        GameSnapshotResponse response = new GameSnapshotResponse(9, gameId, 22, 0,
                new State(33, 3, GamePhase.FINISHED, 2, 2, 1, null, 0, 0,
                        List.of(), List.of(), true, true), List.of(), null, null, true);
        when(games.snapshot(gameId, 7L)).thenReturn(response);

        assertThat(controller.snapshot(participant, gameId)).isSameAs(response);
    }

    @Test
    void outsiderAndUnknownGameRemainForbidden() {
        assertThatThrownBy(() -> controller.snapshot(participant, gameId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void currentParticipantReceivesTheirExactActiveGame() {
        ActiveGameResponse response = new ActiveGameResponse(9, gameId, 22, 33, 4);
        when(games.activeGameForUser(7L)).thenReturn(response);

        assertThat(controller.activeMine(participant)).isSameAs(response);
    }

    @Test
    void existingRoomScopedActiveLookupRemainsUnchanged() {
        ActiveGameResponse response = new ActiveGameResponse(9, gameId, 22, 33, 4);
        when(games.activeGame(9, 7L)).thenReturn(response);

        assertThat(controller.active(participant, 9)).isSameAs(response);
    }

    @Test
    void noCurrentActiveGameReturnsNotFound() {
        assertThatThrownBy(() -> controller.activeMine(participant))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
