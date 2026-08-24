package com.ptit.poker.game.domain.card;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CardTests {

    @Test
    void cardsWithEqualRankAndSuitHaveValueEqualityAndStableHashCode() {
        Card first = new Card(Rank.ACE, Suit.SPADES);
        Card second = new Card(Rank.ACE, Suit.SPADES);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode()).isEqualTo(first.hashCode());
        assertThat(first.toString()).contains("ACE", "SPADES");
    }

    @Test
    void cardsDifferingByRankOrSuitAreNotEqual() {
        Card card = new Card(Rank.ACE, Suit.SPADES);

        assertThat(card)
                .isNotEqualTo(new Card(Rank.KING, Suit.SPADES))
                .isNotEqualTo(new Card(Rank.ACE, Suit.HEARTS));
    }

    @Test
    void nullRankIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Card(null, Suit.SPADES))
                .withMessage("rank must not be null");
    }

    @Test
    void nullSuitIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Card(Rank.ACE, null))
                .withMessage("suit must not be null");
    }
}
