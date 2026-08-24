package com.ptit.poker.game.domain.card;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** A stateful standard 52-card deck whose undrawn cards can be shuffled. */
public final class Deck {

    public static final int STANDARD_CARD_COUNT = 52;

    private final List<Card> cards;
    private final RandomGenerator randomGenerator;
    private int drawPosition;

    /** Creates an ordered fresh deck backed by production runtime randomness. */
    public Deck() {
        this(new SecureRandom());
    }

    /** Creates an ordered fresh deck with injectable randomness for deterministic tests. */
    public Deck(RandomGenerator randomGenerator) {
        this.randomGenerator = Objects.requireNonNull(randomGenerator, "randomGenerator must not be null");
        this.cards = createStandardCards();
    }

    /** Shuffles only cards that have not yet been drawn. */
    public void shuffle() {
        for (int index = cards.size() - 1; index > drawPosition; index--) {
            int swapIndex = drawPosition + randomGenerator.nextInt(index - drawPosition + 1);
            Card card = cards.get(index);
            cards.set(index, cards.get(swapIndex));
            cards.set(swapIndex, card);
        }
    }

    public Card draw() {
        return draw(1).get(0);
    }

    /**
     * Draws exactly {@code count} cards in deck order.
     *
     * @throws IllegalArgumentException if {@code count} is negative
     * @throws DeckExhaustedException if fewer than {@code count} cards remain
     */
    public List<Card> draw(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("draw count must not be negative");
        }
        if (count > remainingCount()) {
            throw new DeckExhaustedException(count, remainingCount());
        }

        int endPosition = drawPosition + count;
        List<Card> drawnCards = List.copyOf(cards.subList(drawPosition, endPosition));
        drawPosition = endPosition;
        return drawnCards;
    }

    public int remainingCount() {
        return cards.size() - drawPosition;
    }

    private static List<Card> createStandardCards() {
        List<Card> standardCards = new ArrayList<>(STANDARD_CARD_COUNT);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                standardCards.add(new Card(rank, suit));
            }
        }
        return standardCards;
    }
}
