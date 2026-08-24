package com.ptit.poker.game.domain.card;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeckTests {

    @Test
    void freshDeckContainsTheCompleteStandardCardSet() {
        Deck deck = new Deck();

        List<Card> cards = deck.draw(Deck.STANDARD_CARD_COUNT);

        assertThat(cards).hasSize(52).doesNotHaveDuplicates();
        assertThat(new HashSet<>(cards)).hasSize(52);
        assertThat(deck.remainingCount()).isZero();
    }

    @Test
    void standardDeckHasFourSuitsAndThirteenRanks() {
        assertThat(Suit.values()).containsExactly(
                Suit.CLUBS, Suit.DIAMONDS, Suit.HEARTS, Suit.SPADES);
        assertThat(Rank.values()).containsExactly(
                Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX,
                Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK,
                Rank.QUEEN, Rank.KING, Rank.ACE);
    }

    @Test
    void everySuitHasThirteenCardsAndEveryRankOccursFourTimes() {
        List<Card> cards = new Deck().draw(52);
        Map<Suit, Integer> suitCounts = new EnumMap<>(Suit.class);
        Map<Rank, Integer> rankCounts = new EnumMap<>(Rank.class);
        cards.forEach(card -> {
            suitCounts.merge(card.suit(), 1, Integer::sum);
            rankCounts.merge(card.rank(), 1, Integer::sum);
        });

        assertThat(suitCounts).hasSize(4).allSatisfy((suit, count) -> assertThat(count).isEqualTo(13));
        assertThat(rankCounts).hasSize(13).allSatisfy((rank, count) -> assertThat(count).isEqualTo(4));
    }

    @Test
    void standardEnumsContainNoJoker() {
        assertThat(Suit.values()).extracting(Enum::name).doesNotContain("JOKER");
        assertThat(Rank.values()).extracting(Enum::name).doesNotContain("JOKER");
    }

    @Test
    void ranksExposeStrengthsFromTwoThroughAce() {
        assertThat(Rank.values()).extracting(Rank::strength)
                .containsExactly(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
    }

    @Test
    void drawingOneCardReducesRemainingCountByOne() {
        Deck deck = new Deck();

        Card drawn = deck.draw();

        assertThat(drawn).isNotNull();
        assertThat(deck.remainingCount()).isEqualTo(51);
    }

    @Test
    void drawingSeveralCardsPreservesOrderAndReducesRemainingCount() {
        Deck singleDrawDeck = new Deck(new Random(10));
        Deck multipleDrawDeck = new Deck(new Random(10));
        singleDrawDeck.shuffle();
        multipleDrawDeck.shuffle();

        List<Card> separatelyDrawn = List.of(
                singleDrawDeck.draw(), singleDrawDeck.draw(), singleDrawDeck.draw());
        List<Card> togetherDrawn = multipleDrawDeck.draw(3);

        assertThat(togetherDrawn).containsExactlyElementsOf(separatelyDrawn);
        assertThat(multipleDrawDeck.remainingCount()).isEqualTo(49);
    }

    @Test
    void drawingAllCardsProducesFiftyTwoUniqueCards() {
        Deck deck = new Deck(new Random(20));
        deck.shuffle();

        List<Card> cards = deck.draw(52);

        assertThat(cards).hasSize(52).doesNotHaveDuplicates();
    }

    @Test
    void drawingAfterExhaustionFailsCleanly() {
        Deck deck = new Deck();
        deck.draw(52);

        assertThatThrownBy(deck::draw)
                .isInstanceOf(DeckExhaustedException.class)
                .hasMessageContaining("only 0 remain");
        assertThat(deck.remainingCount()).isZero();
    }

    @Test
    void oversizedDrawFailsWithoutPartiallyMutatingDeck() {
        Deck deck = new Deck();
        deck.draw(49);

        assertThatThrownBy(() -> deck.draw(4))
                .isInstanceOf(DeckExhaustedException.class)
                .hasMessageContaining("only 3 remain");
        assertThat(deck.remainingCount()).isEqualTo(3);
        assertThat(deck.draw(3)).hasSize(3);
    }

    @Test
    void drawingZeroReturnsAnImmutableEmptyListWithoutMutation() {
        Deck deck = new Deck();

        List<Card> cards = deck.draw(0);

        assertThat(cards).isEmpty();
        assertThatThrownBy(() -> cards.add(new Card(Rank.ACE, Suit.SPADES)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(deck.remainingCount()).isEqualTo(52);
    }

    @Test
    void negativeDrawCountIsRejectedWithoutMutation() {
        Deck deck = new Deck();

        assertThatThrownBy(() -> deck.draw(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
        assertThat(deck.remainingCount()).isEqualTo(52);
    }

    @Test
    void shufflePreservesTheCompleteCardSet() {
        List<Card> unshuffledCards = new Deck().draw(52);
        Deck shuffledDeck = new Deck(new Random(30));

        shuffledDeck.shuffle();
        List<Card> shuffledCards = shuffledDeck.draw(52);

        assertThat(shuffledCards).containsExactlyInAnyOrderElementsOf(unshuffledCards).doesNotHaveDuplicates();
    }

    @Test
    void injectedRandomnessMakesShuffleReproducible() {
        Deck first = new Deck(new Random(40));
        Deck second = new Deck(new Random(40));

        first.shuffle();
        second.shuffle();

        assertThat(first.draw(52)).containsExactlyElementsOf(second.draw(52));
    }

    @Test
    void shuffleDoesNotChangeAlreadyDrawnCardsOrRestoreThem() {
        Deck deck = new Deck(new Random(50));
        Card first = deck.draw();

        deck.shuffle();
        List<Card> remaining = deck.draw(51);

        assertThat(remaining).doesNotContain(first).doesNotHaveDuplicates();
    }
}
