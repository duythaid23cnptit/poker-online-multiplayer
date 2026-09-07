package com.ptit.poker.game.application;

import com.ptit.poker.game.application.runtime.GameRuntimeService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameObservationQueryServiceTests {
    private final GameRuntimeService runtime = mock(GameRuntimeService.class);
    private final HistoricalGameSnapshotPort history = mock(HistoricalGameSnapshotPort.class);
    private final GameObservationQueryService observations = new GameObservationQueryService(runtime, history);
    private final UUID gameId = UUID.randomUUID();

    @Test
    void activeObserverRetainsRuntimeAuthorizationWithoutHistoricalLookup() {
        when(runtime.canObserve(gameId, 7L)).thenReturn(true);

        assertThat(observations.canObserve(gameId, 7L)).isTrue();

        verify(history, never()).isFinishedParticipant(gameId, 7L);
    }

    @Test
    void persistedFinishedParticipantCanObserveAfterRuntimeRemoval() {
        when(history.isFinishedParticipant(gameId, 7L)).thenReturn(true);

        assertThat(observations.canObserve(gameId, 7L)).isTrue();
    }

    @Test
    void outsiderAndUnknownGameRemainUnobservable() {
        assertThat(observations.canObserve(gameId, 99L)).isFalse();
    }
}
