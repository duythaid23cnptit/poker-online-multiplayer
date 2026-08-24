package com.ptit.poker.game.domain.betting;

import java.util.Objects;
import java.util.UUID;

/**
 * A player's betting intent for one authoritative turn.
 * {@code targetCurrentBet} is used only by BET and RAISE and means "raise/bet to",
 * never "raise by".
 */
public record BettingAction(
        long userId,
        UUID turnId,
        PokerActionType type,
        long targetCurrentBet) {

    public BettingAction {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(type, "type must not be null");
        if (targetCurrentBet < 0) {
            throw new IllegalArgumentException("targetCurrentBet must not be negative");
        }
        if (type != PokerActionType.BET && type != PokerActionType.RAISE && targetCurrentBet != 0) {
            throw new IllegalArgumentException("targetCurrentBet is only valid for BET and RAISE");
        }
    }

    public static BettingAction fold(long userId, UUID turnId) {
        return new BettingAction(userId, turnId, PokerActionType.FOLD, 0);
    }

    public static BettingAction check(long userId, UUID turnId) {
        return new BettingAction(userId, turnId, PokerActionType.CHECK, 0);
    }

    public static BettingAction call(long userId, UUID turnId) {
        return new BettingAction(userId, turnId, PokerActionType.CALL, 0);
    }

    public static BettingAction betTo(long userId, UUID turnId, long targetCurrentBet) {
        return new BettingAction(userId, turnId, PokerActionType.BET, targetCurrentBet);
    }

    public static BettingAction raiseTo(long userId, UUID turnId, long targetCurrentBet) {
        return new BettingAction(userId, turnId, PokerActionType.RAISE, targetCurrentBet);
    }

    public static BettingAction allIn(long userId, UUID turnId) {
        return new BettingAction(userId, turnId, PokerActionType.ALL_IN, 0);
    }
}
