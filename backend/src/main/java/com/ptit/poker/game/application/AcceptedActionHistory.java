package com.ptit.poker.game.application;

import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.state.GamePhase;
import java.util.Objects;
import java.util.UUID;

public record AcceptedActionHistory(
        long userId, GamePhase phase, PokerActionType actionType,
        long amountCommittedByAction, long resultingPlayerCurrentBet,
        long resultingGameCurrentBet, long resultingTableChips,
        UUID turnId, UUID clientActionId) {
    public AcceptedActionHistory {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        if (amountCommittedByAction < 0 || resultingPlayerCurrentBet < 0
                || resultingGameCurrentBet < 0 || resultingTableChips < 0) {
            throw new IllegalArgumentException("accepted action chip values must not be negative");
        }
    }
}
