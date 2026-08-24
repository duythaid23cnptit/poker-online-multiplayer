package com.ptit.poker.game.domain.card;

/** Raised when a draw requests more cards than remain in the deck. */
public final class DeckExhaustedException extends IllegalStateException {

    public DeckExhaustedException(int requested, int remaining) {
        super("Cannot draw " + requested + " cards: only " + remaining + " remain");
    }
}
