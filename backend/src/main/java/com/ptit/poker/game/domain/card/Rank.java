package com.ptit.poker.game.domain.card;

/** Card ranks ordered by their ordinary high-card poker strength. */
public enum Rank {
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),
    JACK(11),
    QUEEN(12),
    KING(13),
    ACE(14);

    private final int strength;

    Rank(int strength) {
        this.strength = strength;
    }

    public int strength() {
        return strength;
    }
}
