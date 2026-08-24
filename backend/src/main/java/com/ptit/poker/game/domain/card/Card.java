package com.ptit.poker.game.domain.card;

import java.util.Objects;

/** An immutable playing-card value. */
public record Card(Rank rank, Suit suit) {

    public Card {
        Objects.requireNonNull(rank, "rank must not be null");
        Objects.requireNonNull(suit, "suit must not be null");
    }
}
