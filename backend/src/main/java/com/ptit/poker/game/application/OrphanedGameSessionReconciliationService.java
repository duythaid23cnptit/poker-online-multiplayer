package com.ptit.poker.game.application;

import com.ptit.poker.game.application.runtime.RoomGamePort;
import com.ptit.poker.game.infrastructure.persistence.GameSessionEntity;
import com.ptit.poker.game.infrastructure.persistence.GameSessionRepository;
import com.ptit.poker.game.infrastructure.persistence.GameSessionStatus;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class OrphanedGameSessionReconciliationService {
    private final GameSessionRepository sessions;
    private final RoomGamePort rooms;
    private final Clock clock;

    public OrphanedGameSessionReconciliationService(GameSessionRepository sessions, RoomGamePort rooms, Clock clock) {
        this.sessions = sessions;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Transactional
    public ReconciliationResult reconcile() {
        int sessionsAborted = 0;
        int membershipsFinalized = 0;
        long chipsRefunded = 0;
        int roomsFinished = 0;
        for (long sessionId : sessions.findIdsByStatus(GameSessionStatus.ACTIVE)) {
            GameSessionEntity session = sessions.findByIdForUpdate(sessionId).orElse(null);
            if (session == null || session.getStatus() != GameSessionStatus.ACTIVE) continue;
            RoomGamePort.OrphanedRoomFinalization room = rooms.finalizeOrphanedGame(session.getRoomId());
            session.abort(clock.instant());
            sessionsAborted++;
            membershipsFinalized = Math.addExact(membershipsFinalized, room.membershipsFinalized());
            chipsRefunded = Math.addExact(chipsRefunded, room.chipsRefunded());
            if (room.roomFinished()) roomsFinished++;
        }
        sessions.flush();
        return new ReconciliationResult(sessionsAborted, membershipsFinalized, chipsRefunded, roomsFinished);
    }

    public record ReconciliationResult(
            int sessionsAborted,
            int membershipsFinalized,
            long chipsRefunded,
            int roomsFinished) {}
}
