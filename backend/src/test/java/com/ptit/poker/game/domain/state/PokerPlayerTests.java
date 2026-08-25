package com.ptit.poker.game.domain.state;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokerPlayerTests {

    @Test
    void constructsPlayerWithAuthoritativeChipAndIdentityState() {
        PokerPlayer player = player(10, 3, 1_000, 20, 80, PokerPlayerState.ACTIVE, List.of());

        assertThat(player.userId()).isEqualTo(10);
        assertThat(player.seatNumber()).isEqualTo(3);
        assertThat(player.tableChips()).isEqualTo(1_000);
        assertThat(player.currentBet()).isEqualTo(20);
        assertThat(player.totalCommitted()).isEqualTo(80);
        assertThat(player.playerState()).isEqualTo(PokerPlayerState.ACTIVE);
        assertThat(player.holeCards()).isEmpty();
    }

    @Test
    void acceptsExactlyTwoUniqueHoleCardsAndKeepsThemImmutable() {
        List<Card> supplied = new ArrayList<>(List.of(card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.HEARTS)));
        PokerPlayer player = player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE, supplied);

        supplied.clear();

        assertThat(player.holeCards()).hasSize(2);
        assertThatThrownBy(() -> player.holeCards().add(card(Rank.QUEEN, Suit.CLUBS)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsOneOrMoreThanTwoHoleCards() {
        assertThatThrownBy(() -> player(10, 1, 100, 0, 0, PokerPlayerState.ACTIVE,
                List.of(card(Rank.ACE, Suit.SPADES))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0 or 2");
        assertThatThrownBy(() -> player(10, 1, 100, 0, 0, PokerPlayerState.ACTIVE,
                List.of(card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.SPADES),
                        card(Rank.QUEEN, Suit.SPADES))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0 or 2");
    }

    @Test
    void rejectsDuplicateHoleCards() {
        Card ace = card(Rank.ACE, Suit.SPADES);

        assertThatThrownBy(() -> player(10, 1, 100, 0, 0, PokerPlayerState.ACTIVE, List.of(ace, ace)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicates");
    }

    @Test
    void rejectsNegativeChipValuesIndependently() {
        assertThatThrownBy(() -> player(10, 1, -1, 0, 0, PokerPlayerState.ACTIVE, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tableChips");
        assertThatThrownBy(() -> player(10, 1, 0, -1, 0, PokerPlayerState.ACTIVE, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currentBet");
        assertThatThrownBy(() -> player(10, 1, 0, 0, -1, PokerPlayerState.ACTIVE, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("totalCommitted");
    }

    @Test
    void rejectsInvalidIdentitySeatAndState() {
        assertThatThrownBy(() -> player(0, 1, 0, 0, 0, PokerPlayerState.ACTIVE, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userId");
        assertThatThrownBy(() -> player(10, 0, 0, 0, 0, PokerPlayerState.ACTIVE, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seatNumber");
        assertThatThrownBy(() -> player(10, 10, 0, 0, 0, PokerPlayerState.ACTIVE, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seatNumber");
        assertThatThrownBy(() -> player(10, 1, 0, 0, 0, null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("playerState");
    }

    @Test
    void participationConnectivityAndLeavingAreIndependentDomainFacts() {
        PokerPlayer folded = player(10, 1, 100, 0, 20, PokerPlayerState.ACTIVE, List.of());
        folded.markFolded();
        folded.markDisconnected();
        assertThat(folded.playerState()).isEqualTo(PokerPlayerState.FOLDED);
        assertThat(folded.isDisconnected()).isTrue();

        PokerPlayer allIn = player(20, 2, 0, 100, 100, PokerPlayerState.ALL_IN, List.of());
        allIn.markDisconnected();
        assertThat(allIn.playerState()).isEqualTo(PokerPlayerState.ALL_IN);
        assertThat(allIn.isDisconnected()).isTrue();

        PokerPlayer leaving = player(30, 3, 80, 20, 20, PokerPlayerState.ACTIVE, List.of());
        leaving.markLeaving();
        assertThat(leaving.playerState()).isEqualTo(PokerPlayerState.FOLDED);
        assertThat(leaving.isLeaving()).isTrue();
        assertThat(leaving.totalCommitted()).isEqualTo(20);
    }

    @Test
    void supportsEveryApprovedPlayerStateAtConstruction() {
        for (PokerPlayerState state : PokerPlayerState.values()) {
            assertThat(player(10, 1, 100, 0, 0, state, List.of()).playerState()).isEqualTo(state);
        }
        assertThat(PokerPlayerState.values()).containsExactly(
                PokerPlayerState.ACTIVE,
                PokerPlayerState.FOLDED,
                PokerPlayerState.ALL_IN);
    }

    @Test
    void dealsHoleCardsExactlyOnceAfterBlindPosting() {
        PokerPlayer player = player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE, List.of());
        player.commitChips(50);
        List<Card> dealt = List.of(card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.HEARTS));

        player.dealHoleCards(dealt);

        assertThat(player.tableChips()).isEqualTo(950);
        assertThat(player.totalCommitted()).isEqualTo(50);
        assertThat(player.holeCards()).containsExactlyElementsOf(dealt);
        assertThatThrownBy(() -> player.dealHoleCards(dealt))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already dealt");
    }

    private static PokerPlayer player(
            long userId,
            int seat,
            long chips,
            long bet,
            long committed,
            PokerPlayerState state,
            List<Card> holeCards) {
        return new PokerPlayer(userId, seat, chips, bet, committed, state, holeCards);
    }

    private static Card card(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }
}
