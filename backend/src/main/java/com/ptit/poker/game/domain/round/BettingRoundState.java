package com.ptit.poker.game.domain.round;

import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable internal bookkeeping for one street's betting participation and raise rights. */
public final class BettingRoundState {

    private GamePhase phase;
    private final Set<Long> pendingResponses = new LinkedHashSet<>();
    private final Map<Long, Long> lastActionBetLevels = new HashMap<>();

    BettingRoundState(GamePhase phase, Set<Long> initialPendingResponses) {
        reset(phase, initialPendingResponses);
    }

    public GamePhase phase() {
        return phase;
    }

    public Set<Long> pendingResponses() {
        return Set.copyOf(pendingResponses);
    }

    public boolean needsResponse(long userId) {
        return pendingResponses.contains(userId);
    }

    public boolean raiseRightsOpen(long userId, GameState gameState) {
        Long lastActionLevel = lastActionBetLevels.get(userId);
        if (lastActionLevel == null) {
            return true;
        }
        return gameState.currentBet() - lastActionLevel >= gameState.minimumRaise();
    }

    void recordOrdinaryAction(long userId, long currentBetLevel) {
        pendingResponses.remove(userId);
        lastActionBetLevels.put(userId, currentBetLevel);
    }

    void recordFullRaise(long actorId, long currentBetLevel, Set<Long> actionablePlayers) {
        pendingResponses.clear();
        pendingResponses.addAll(actionablePlayers);
        pendingResponses.remove(actorId);
        lastActionBetLevels.clear();
        lastActionBetLevels.put(actorId, currentBetLevel);
    }

    void requireResponsesFrom(Set<Long> userIds) {
        pendingResponses.addAll(userIds);
    }

    void removeIneligible(Set<Long> actionablePlayers) {
        pendingResponses.retainAll(actionablePlayers);
    }

    void reset(GamePhase newPhase, Set<Long> initialPendingResponses) {
        phase = Objects.requireNonNull(newPhase, "phase must not be null");
        pendingResponses.clear();
        pendingResponses.addAll(initialPendingResponses);
        lastActionBetLevels.clear();
    }
}
