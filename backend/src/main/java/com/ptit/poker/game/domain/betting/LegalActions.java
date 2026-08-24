package com.ptit.poker.game.domain.betting;

import java.util.Objects;
import java.util.Set;

/** Server-calculated legal actions and authoritative wager bounds for one player. */
public record LegalActions(
        Set<PokerActionType> actions,
        long callAmount,
        long minimumTarget,
        long maximumTarget) {

    public LegalActions {
        Objects.requireNonNull(actions, "actions must not be null");
        if (actions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("actions must not contain null");
        }
        if (callAmount < 0 || minimumTarget < 0 || maximumTarget < 0) {
            throw new IllegalArgumentException("legal-action amounts must not be negative");
        }
        actions = Set.copyOf(actions);
    }

    public boolean allows(PokerActionType action) {
        return actions.contains(action);
    }

    public static LegalActions none() {
        return new LegalActions(Set.of(), 0, 0, 0);
    }
}
