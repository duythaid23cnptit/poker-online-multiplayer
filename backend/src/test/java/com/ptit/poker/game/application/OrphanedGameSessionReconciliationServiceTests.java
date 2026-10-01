package com.ptit.poker.game.application;

import com.ptit.poker.game.application.runtime.RoomGamePort;
import com.ptit.poker.game.infrastructure.persistence.GameSessionEntity;
import com.ptit.poker.game.infrastructure.persistence.GameSessionRepository;
import com.ptit.poker.game.infrastructure.persistence.GameSessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrphanedGameSessionReconciliationServiceTests {
    private static final Instant NOW = Instant.parse("2026-09-10T01:00:00Z");

    @Test
    void abortsLockedActiveSessionAfterRoomMembershipsAreFinalized() {
        GameSessionRepository sessions = mock(GameSessionRepository.class);
        RoomGamePort rooms = mock(RoomGamePort.class);
        GameSessionEntity session = new GameSessionEntity(
                12L, GameSessionStatus.ACTIVE, NOW.minusSeconds(60), null);
        when(sessions.findIdsByStatus(GameSessionStatus.ACTIVE)).thenReturn(List.of(7L));
        when(sessions.findByIdForUpdate(7L)).thenReturn(Optional.of(session));
        when(rooms.finalizeOrphanedGame(12L)).thenReturn(
                new RoomGamePort.OrphanedRoomFinalization(2, 1_000, true));
        var service = service(sessions, rooms);

        var result = service.reconcile();

        assertThat(session.getStatus()).isEqualTo(GameSessionStatus.ABORTED);
        assertThat(session.getEndedAt()).isEqualTo(NOW);
        assertThat(result).isEqualTo(new OrphanedGameSessionReconciliationService.ReconciliationResult(
                1, 2, 1_000, 1));
        verify(rooms).finalizeOrphanedGame(12L);
        verify(sessions).flush();
    }

    @Test
    void rechecksLockedStatusAndSkipsSessionAlreadyFinalizedByAnotherReconciler() {
        GameSessionRepository sessions = mock(GameSessionRepository.class);
        RoomGamePort rooms = mock(RoomGamePort.class);
        GameSessionEntity finished = new GameSessionEntity(
                12L, GameSessionStatus.FINISHED, NOW.minusSeconds(120), NOW.minusSeconds(30));
        when(sessions.findIdsByStatus(GameSessionStatus.ACTIVE)).thenReturn(List.of(7L));
        when(sessions.findByIdForUpdate(7L)).thenReturn(Optional.of(finished));

        var result = service(sessions, rooms).reconcile();

        assertThat(result.sessionsAborted()).isZero();
        verify(rooms, never()).finalizeOrphanedGame(12L);
    }

    @Test
    void startupHookInvokesReconciliationOnce() {
        OrphanedGameSessionReconciliationService service = mock(OrphanedGameSessionReconciliationService.class);
        when(service.reconcile()).thenReturn(
                new OrphanedGameSessionReconciliationService.ReconciliationResult(0, 0, 0, 0));
        var startup = new OrphanedGameSessionStartupReconciler(service);

        startup.afterSingletonsInstantiated();

        verify(service).reconcile();
    }

    private static OrphanedGameSessionReconciliationService service(
            GameSessionRepository sessions, RoomGamePort rooms) {
        return new OrphanedGameSessionReconciliationService(
                sessions, rooms, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
