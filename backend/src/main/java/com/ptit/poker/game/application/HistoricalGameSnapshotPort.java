package com.ptit.poker.game.application;

import java.util.Optional;
import java.util.UUID;

public interface HistoricalGameSnapshotPort {
    Optional<HistoricalGameSnapshot> findFinishedForParticipant(UUID gameId, long userId);
    boolean isFinishedParticipant(UUID gameId, long userId);
}
