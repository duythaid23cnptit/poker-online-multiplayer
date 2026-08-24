package com.ptit.poker.game.domain.betting;

/** Immutable authoritative outcome of one accepted betting action. */
public record BettingResult(
        PokerActionType actionType,
        long userId,
        long amountCommitted,
        long resultingCurrentBet,
        boolean allIn,
        boolean fullRaise) {
}
