package com.ptit.poker.game.domain.state;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameStateTests {

    private static final UUID GAME_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HAND_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TURN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void constructsValidAuthoritativeGameStateWithDistinctSessionAndHandIds() {
        GameState state = state(GamePhase.PRE_FLOP, List.of(), players(), 10L, 4, TURN_ID);

        assertThat(state.gameId()).isEqualTo(GAME_ID).isNotEqualTo(state.handId());
        assertThat(state.handId()).isEqualTo(HAND_ID);
        assertThat(state.phase()).isEqualTo(GamePhase.PRE_FLOP);
        assertThat(state.dealerPosition()).isEqualTo(1);
        assertThat(state.smallBlindPosition()).isEqualTo(2);
        assertThat(state.bigBlindPosition()).isEqualTo(3);
        assertThat(state.currentTurnUserId()).isEqualTo(10);
        assertThat(state.currentBet()).isEqualTo(20);
        assertThat(state.minimumRaise()).isEqualTo(20);
        assertThat(state.potConstruction().mainPot()).isEmpty();
        assertThat(state.remainingTime()).isEqualTo(Duration.ofSeconds(30));
        assertThat(state.stateVersion()).isEqualTo(4);
        assertThat(state.turnId()).isEqualTo(TURN_ID);
    }

    @Test
    void exposesExactlyTheApprovedGamePhases() {
        assertThat(GamePhase.values()).containsExactly(
                GamePhase.PRE_FLOP,
                GamePhase.FLOP,
                GamePhase.TURN,
                GamePhase.RIVER,
                GamePhase.SHOWDOWN,
                GamePhase.FINISHED);
    }

    @Test
    void rejectsNullPhaseAndEqualSessionAndHandIdentifiers() {
        assertThatThrownBy(() -> state(null, List.of(), players(), 10L, 0, TURN_ID))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("phase");
        assertThatThrownBy(() -> new GameState(
                GAME_ID, GAME_ID, GamePhase.PRE_FLOP, 1, 2, 3, 10L,
                0, 0, List.of(), players(), Duration.ZERO, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct");
    }

    @Test
    void rejectsDuplicateUsersAndDuplicateSeats() {
        List<PokerPlayer> duplicateUsers = List.of(player(10, 1), player(10, 2), player(30, 3));
        List<PokerPlayer> duplicateSeats = List.of(player(10, 1), player(20, 1), player(30, 3));

        assertThatThrownBy(() -> state(GamePhase.PRE_FLOP, List.of(), duplicateUsers, 10L, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate userId");
        assertThatThrownBy(() -> state(GamePhase.PRE_FLOP, List.of(), duplicateSeats, 10L, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate seatNumber");
    }

    @Test
    void ordersPlayersDeterministicallyBySeatWithoutMutatingInput() {
        List<PokerPlayer> supplied = new ArrayList<>(List.of(player(30, 3), player(10, 1), player(20, 2)));

        GameState state = state(GamePhase.PRE_FLOP, List.of(), supplied, 10L, 0, TURN_ID);

        assertThat(state.players()).extracting(PokerPlayer::seatNumber).containsExactly(1, 2, 3);
        assertThat(supplied).extracting(PokerPlayer::seatNumber).containsExactly(3, 1, 2);
    }

    @Test
    void rejectsUnoccupiedOrInvalidAuthoritativePositions() {
        assertThatThrownBy(() -> new GameState(
                GAME_ID, HAND_ID, GamePhase.PRE_FLOP, 0, 2, 3, 10L,
                0, 0, List.of(), players(), Duration.ZERO, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dealerPosition");
        assertThatThrownBy(() -> new GameState(
                GAME_ID, HAND_ID, GamePhase.PRE_FLOP, 1, 2, 4, 10L,
                0, 0, List.of(), players(), Duration.ZERO, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("occupied seat");
    }

    @Test
    void supportsNinePlayersAtTheRoomMaximum() {
        List<PokerPlayer> ninePlayers = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(seat -> player(seat * 10L, seat))
                .toList();

        GameState state = new GameState(
                GAME_ID, HAND_ID, GamePhase.PRE_FLOP, 1, 2, 3, 10L,
                0, 0, List.of(), ninePlayers, Duration.ZERO, 0, TURN_ID);

        assertThat(state.players()).hasSize(9);
    }

    @Test
    void validatesCommunityCardCountsForEveryStreet() {
        assertThat(state(GamePhase.PRE_FLOP, List.of(), players(), 10L, 0, TURN_ID).communityCards()).isEmpty();
        assertThat(state(GamePhase.FLOP, cards("2C", "3D", "4H"), players(), 10L, 0, TURN_ID)
                .communityCards()).hasSize(3);
        assertThat(state(GamePhase.TURN, cards("2C", "3D", "4H", "5S"), players(), 10L, 0, TURN_ID)
                .communityCards()).hasSize(4);
        assertThat(state(GamePhase.RIVER, cards("2C", "3D", "4H", "5S", "6C"), players(), 10L, 0, TURN_ID)
                .communityCards()).hasSize(5);
        assertThat(state(GamePhase.SHOWDOWN, cards("2C", "3D", "4H", "5S", "6C"), players(), null, 0, null)
                .communityCards()).hasSize(5);
    }

    @Test
    void rejectsStreetCardCountMismatchAndMoreThanFiveCards() {
        assertThatThrownBy(() -> state(GamePhase.FLOP, List.of(), players(), 10L, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires 3");
        assertThatThrownBy(() -> state(GamePhase.FINISHED,
                cards("2C", "3D", "4H", "5S", "6C", "7D"), players(), null, 0, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("more than 5");
    }

    @Test
    void rejectsDuplicateCommunityCards() {
        Card duplicate = card("2C");

        assertThatThrownBy(() -> state(
                GamePhase.FLOP, List.of(duplicate, duplicate, card("3D")), players(), 10L, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate known cards");
    }

    @Test
    void rejectsCommunityCardThatDuplicatesHoleCard() {
        PokerPlayer dealtPlayer = new PokerPlayer(
                10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE, cards("AS", "KH"));
        List<PokerPlayer> dealtPlayers = List.of(dealtPlayer, player(20, 2), player(30, 3));

        assertThatThrownBy(() -> state(
                GamePhase.FLOP, cards("AS", "2C", "3D"), dealtPlayers, 10L, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate known cards");
    }

    @Test
    void rejectsSameHoleCardAcrossDifferentPlayers() {
        List<PokerPlayer> dealtPlayers = List.of(
                new PokerPlayer(10, 1, 100, 0, 0, PokerPlayerState.ACTIVE, cards("AS", "KH")),
                new PokerPlayer(20, 2, 100, 0, 0, PokerPlayerState.ACTIVE, cards("AS", "QD")),
                player(30, 3));

        assertThatThrownBy(() -> state(GamePhase.PRE_FLOP, List.of(), dealtPlayers, 10L, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unique across the game");
    }

    @Test
    void communityCardsAndPlayerCollectionCannotBeMutatedExternally() {
        List<Card> board = new ArrayList<>(cards("2C", "3D", "4H"));
        List<PokerPlayer> suppliedPlayers = new ArrayList<>(players());
        GameState state = state(GamePhase.FLOP, board, suppliedPlayers, 10L, 0, TURN_ID);

        board.clear();
        suppliedPlayers.clear();

        assertThat(state.communityCards()).hasSize(3);
        assertThat(state.players()).hasSize(3);
        assertThatThrownBy(() -> state.communityCards().add(card("5S")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.players().add(player(40, 4)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNegativeGameChipValuesAndRemainingTime() {
        assertInvalidGameAmount(-1, 0, "currentBet");
        assertInvalidGameAmount(0, -1, "minimumRaise");
        assertThatThrownBy(() -> new GameState(
                GAME_ID, HAND_ID, GamePhase.PRE_FLOP, 1, 2, 3, 10L,
                0, 0, List.of(), players(), Duration.ofSeconds(-1), 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("remainingTime");
    }

    @Test
    void stateVersionRejectsNegativeAndAdvancesMonotonically() {
        assertThatThrownBy(() -> state(GamePhase.PRE_FLOP, List.of(), players(), 10L, -1, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stateVersion");
        GameState state = state(GamePhase.PRE_FLOP, List.of(), players(), 10L, 7, TURN_ID);

        assertThat(state.advanceStateVersion()).isEqualTo(8);
        assertThat(state.advanceStateVersion()).isEqualTo(9);
        assertThat(state.stateVersion()).isEqualTo(9);
    }

    @Test
    void stateVersionCannotOverflow() {
        GameState state = state(GamePhase.PRE_FLOP, List.of(), players(), 10L, Long.MAX_VALUE, TURN_ID);

        assertThatThrownBy(state::advanceStateVersion)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Long.MAX_VALUE");
        assertThat(state.stateVersion()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void currentTurnMustReferencePlayerAndHaveMatchingTurnIdPresence() {
        assertThatThrownBy(() -> state(GamePhase.PRE_FLOP, List.of(), players(), 99L, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currentTurnUserId");
        assertThatThrownBy(() -> state(GamePhase.PRE_FLOP, List.of(), players(), 10L, 0, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("both be present");
        assertThatThrownBy(() -> state(GamePhase.PRE_FLOP, List.of(), players(), null, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("both be present");
    }

    @Test
    void showdownAndFinishedAllowNoCurrentTurnOrTurnId() {
        GameState showdown = state(
                GamePhase.SHOWDOWN, cards("2C", "3D", "4H", "5S", "6C"), players(), null, 0, null);
        GameState finished = state(GamePhase.FINISHED, List.of(), players(), null, 0, null);

        assertThat(showdown.currentTurnUserId()).isNull();
        assertThat(showdown.turnId()).isNull();
        assertThat(finished.currentTurnUserId()).isNull();
        assertThat(finished.turnId()).isNull();
    }

    private static GameState state(
            GamePhase phase,
            List<Card> board,
            List<PokerPlayer> players,
            Long currentTurn,
            long version,
            UUID turnId) {
        return new GameState(
                GAME_ID,
                HAND_ID,
                phase,
                1,
                2,
                3,
                currentTurn,
                20,
                20,
                board,
                players,
                Duration.ofSeconds(30),
                version,
                turnId);
    }

    private static List<PokerPlayer> players() {
        return List.of(player(10, 1), player(20, 2), player(30, 3));
    }

    private static PokerPlayer player(long userId, int seat) {
        return new PokerPlayer(userId, seat, 1_000, 0, 0, PokerPlayerState.ACTIVE, List.of());
    }

    private static void assertInvalidGameAmount(long currentBet, long minimumRaise, String field) {
        assertThatThrownBy(() -> new GameState(
                GAME_ID, HAND_ID, GamePhase.PRE_FLOP, 1, 2, 3, 10L,
                currentBet, minimumRaise, List.of(), players(), Duration.ZERO, 0, TURN_ID))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(field);
    }

    private static List<Card> cards(String... codes) {
        return java.util.Arrays.stream(codes).map(GameStateTests::card).toList();
    }

    private static Card card(String code) {
        Rank rank = switch (code.charAt(0)) {
            case '2' -> Rank.TWO;
            case '3' -> Rank.THREE;
            case '4' -> Rank.FOUR;
            case '5' -> Rank.FIVE;
            case '6' -> Rank.SIX;
            case '7' -> Rank.SEVEN;
            case 'Q' -> Rank.QUEEN;
            case 'K' -> Rank.KING;
            case 'A' -> Rank.ACE;
            default -> throw new IllegalArgumentException("Unknown test rank: " + code);
        };
        Suit suit = switch (code.charAt(1)) {
            case 'C' -> Suit.CLUBS;
            case 'D' -> Suit.DIAMONDS;
            case 'H' -> Suit.HEARTS;
            case 'S' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("Unknown test suit: " + code);
        };
        return new Card(rank, suit);
    }
}
