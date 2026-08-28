package com.ptit.poker.game.application;

import com.ptit.poker.game.infrastructure.persistence.GameSessionEntity;
import com.ptit.poker.game.infrastructure.persistence.GameSessionRepository;
import com.ptit.poker.game.infrastructure.persistence.GameSessionStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Profile("!bootstrap")
public class GameSessionPersistenceService {
    private final GameSessionRepository sessions;
    private final RoomExistencePort rooms;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public GameSessionPersistenceService(GameSessionRepository sessions, RoomExistencePort rooms, Clock clock,
                                         ApplicationEventPublisher events) {
        this.sessions = sessions; this.rooms = rooms; this.clock = clock;this.events=events;
    }
    @Transactional
    public GameSessionView startSession(long roomId) {
        if (roomId <= 0 || !rooms.exists(roomId)) throw error("ROOM_NOT_FOUND", "Room not found");
        return view(sessions.saveAndFlush(new GameSessionEntity(
                roomId, GameSessionStatus.ACTIVE, clock.instant(), null)));
    }
    @Transactional public GameSessionView finishSession(long id) { return transition(id, false); }
    @Transactional public GameSessionView abortSession(long id) { return transition(id, true); }
    private GameSessionView transition(long id, boolean abort) {
        GameSessionEntity session = sessions.findByIdForUpdate(id)
                .orElseThrow(() -> error("SESSION_NOT_FOUND", "Game session not found"));
        try {
            if (abort) session.abort(clock.instant()); else session.finish(clock.instant());
        } catch (IllegalStateException exception) {
            throw error("INVALID_SESSION_TRANSITION", exception.getMessage());
        }
        GameSessionView result=view(sessions.saveAndFlush(session));
        if(!abort)events.publishEvent(new GameSessionFinishedEvent(id));
        return result;
    }
    private static GameSessionView view(GameSessionEntity entity) {
        return new GameSessionView(entity.getId(), entity.getRoomId(), entity.getStatus(),
                entity.getStartedAt(), entity.getEndedAt());
    }
    private static GameplayHistoryException error(String code, String message) {
        return new GameplayHistoryException(code, message);
    }
}
