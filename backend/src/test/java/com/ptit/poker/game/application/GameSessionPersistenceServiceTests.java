package com.ptit.poker.game.application;

import com.ptit.poker.game.infrastructure.persistence.GameSessionEntity;
import com.ptit.poker.game.infrastructure.persistence.GameSessionRepository;
import com.ptit.poker.game.infrastructure.persistence.GameSessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSessionPersistenceServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private final GameSessionRepository repository = mock(GameSessionRepository.class);
    private final RoomExistencePort rooms = mock(RoomExistencePort.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final GameSessionPersistenceService service = new GameSessionPersistenceService(
            repository, rooms, Clock.fixed(NOW, ZoneOffset.UTC), events);

    @Test
    void missingRoomIsRejectedBeforePersistence() {
        when(rooms.exists(44L)).thenReturn(false);

        assertThatThrownBy(() -> service.startSession(44L))
                .isInstanceOfSatisfying(GameplayHistoryException.class,
                        error -> assertThat(error.code()).isEqualTo("ROOM_NOT_FOUND"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void invalidSecondTerminalTransitionIsRejected() {
        GameSessionEntity session = new GameSessionEntity(44L, GameSessionStatus.ACTIVE, NOW, null);
        session.finish(NOW);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.abortSession(7L))
                .isInstanceOfSatisfying(GameplayHistoryException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_SESSION_TRANSITION"));
    }

    @Test
    void startUsesInjectedClockAndActiveStatus() {
        when(rooms.exists(44L)).thenReturn(true);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            GameSessionEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 9L);
            return entity;
        });

        service.startSession(44L);

        ArgumentCaptor<GameSessionEntity> saved = ArgumentCaptor.forClass(GameSessionEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GameSessionStatus.ACTIVE);
        assertThat(saved.getValue().getStartedAt()).isEqualTo(NOW);
    }
}
