package com.ptit.poker.game.application;

import com.ptit.poker.game.application.runtime.GameRuntimeService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!bootstrap")
public class GameObservationQueryService {
    private final GameRuntimeService runtime;
    private final HistoricalGameSnapshotPort history;

    public GameObservationQueryService(GameRuntimeService runtime, HistoricalGameSnapshotPort history) {
        this.runtime = runtime;
        this.history = history;
    }

    public boolean canObserve(UUID gameId, long userId) {
        return runtime.canObserve(gameId, userId) || history.isFinishedParticipant(gameId, userId);
    }

    public Optional<HistoricalGameSnapshot> finishedSnapshot(UUID gameId, long userId) {
        return history.findFinishedForParticipant(gameId, userId);
    }
}
