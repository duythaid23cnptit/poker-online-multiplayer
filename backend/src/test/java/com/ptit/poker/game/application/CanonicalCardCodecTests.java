package com.ptit.poker.game.application;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalCardCodecTests {
    @Test
    void roundTripPreservesCanonicalCardOrderIncludingTen() {
        List<Card> cards = List.of(
                new Card(Rank.KING, Suit.CLUBS),
                new Card(Rank.QUEEN, Suit.DIAMONDS),
                new Card(Rank.JACK, Suit.HEARTS),
                new Card(Rank.TEN, Suit.SPADES),
                new Card(Rank.ACE, Suit.CLUBS));

        String encoded = CanonicalCardCodec.serialize(cards);

        assertThat(encoded).isEqualTo("KC,QD,JH,TS,AC");
        assertThat(CanonicalCardCodec.deserialize(encoded)).containsExactlyElementsOf(cards);
    }

    @Test
    void emptyCardsRoundTripAndMalformedCardsAreRejected() {
        assertThat(CanonicalCardCodec.serialize(List.of())).isEmpty();
        assertThat(CanonicalCardCodec.deserialize("")).isEmpty();
        assertThatThrownBy(() -> CanonicalCardCodec.deserialize("10H"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalCardCodec.deserialize("TZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
