package com.ptit.poker.game.domain.betting;

/** Raised when a betting intent violates authoritative Poker state or rules. */
public final class BettingRuleViolationException extends IllegalArgumentException {

    public BettingRuleViolationException(String message) {
        super(message);
    }
}
