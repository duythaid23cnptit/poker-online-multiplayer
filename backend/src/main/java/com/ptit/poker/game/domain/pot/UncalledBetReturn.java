package com.ptit.poker.game.domain.pot;

/** Chips committed above every opponent's matched contribution. */
public record UncalledBetReturn(long userId, long amount) {

    public UncalledBetReturn {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
