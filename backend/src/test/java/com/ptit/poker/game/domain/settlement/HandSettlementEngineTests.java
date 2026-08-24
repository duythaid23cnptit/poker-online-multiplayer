package com.ptit.poker.game.domain.settlement;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import com.ptit.poker.game.domain.hand.HandCategory;
import com.ptit.poker.game.domain.hand.HandEvaluator;
import com.ptit.poker.game.domain.hand.HandValue;
import com.ptit.poker.game.domain.pot.Pot;
import com.ptit.poker.game.domain.pot.PotType;
import com.ptit.poker.game.domain.pot.UncalledBetReturn;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandSettlementEngineTests {

    private static final UUID GAME_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HAND_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final HandSettlementEngine engine = new HandSettlementEngine(new HandEvaluator());

    @Test
    void strongestEligibleHandWinsWholeMainPotAndSettlementResetsCommitments() {
        PokerPlayer winner = player(1, 1, 900, 100, PokerPlayerState.ACTIVE, "KH", "KD");
        PokerPlayer loser = player(2, 2, 900, 100, PokerPlayerState.ACTIVE, "AH", "QD");
        GameState game = showdown(List.of(winner, loser), 2, "KC", "9S", "7H", "3D", "2C");

        HandSettlementResult result = engine.settleShowdown(game);

        assertThat(result.potAwards()).hasSize(1);
        assertThat(result.potAwards().getFirst().winnerUserIds()).containsExactly(1L);
        assertThat(result.potAwards().getFirst().winningHandValue().orElseThrow().category())
                .isEqualTo(HandCategory.THREE_OF_A_KIND);
        assertThat(winner.tableChips()).isEqualTo(1_100);
        assertThat(loser.tableChips()).isEqualTo(900);
        assertThat(game.players()).allSatisfy(player -> {
            assertThat(player.currentBet()).isZero();
            assertThat(player.totalCommitted()).isZero();
        });
        assertThat(game.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(game.isFinanciallySettled()).isTrue();
        assertConserved(result);
    }

    @Test
    void existingEvaluatorRanksStraightFlushAndFullHouseCorrectlyAtSettlement() {
        assertWinnerCategory(
                List.of("8C", "9D", "TH", "JS", "2C"),
                List.of("QC", "3D"), List.of("2D", "2H"),
                1, HandCategory.STRAIGHT);
        assertWinnerCategory(
                List.of("2H", "5H", "9H", "JS", "KC"),
                List.of("AH", "3H"), List.of("QD", "TC"),
                1, HandCategory.FLUSH);
        assertWinnerCategory(
                List.of("2C", "2D", "5H", "9S", "KC"),
                List.of("2H", "5D"), List.of("AC", "QC"),
                1, HandCategory.FULL_HOUSE);
    }

    @Test
    void boardRoyalFlushTiesWithoutSuitBreakingAndSplitsEqually() {
        PokerPlayer first = player(1, 1, 900, 100, PokerPlayerState.ACTIVE, "2C", "3D");
        PokerPlayer second = player(2, 2, 900, 100, PokerPlayerState.ACTIVE, "AH", "AD");
        GameState game = showdown(List.of(first, second), 1, "AS", "KS", "QS", "JS", "TS");

        PotAward award = engine.settleShowdown(game).potAwards().getFirst();

        assertThat(award.winnerUserIds()).containsExactly(2L, 1L);
        assertThat(award.winningHandValue().orElseThrow().category()).isEqualTo(HandCategory.ROYAL_FLUSH);
        assertThat(award.winnerPayouts()).containsEntry(1L, 100L).containsEntry(2L, 100L);
    }

    @Test
    void oddChipGoesClockwiseAfterDealerNotByUserId() {
        PokerPlayer dealerFolded = player(50, 1, 997, 3, PokerPlayerState.FOLDED);
        PokerPlayer clockwiseFirst = player(99, 2, 997, 3, PokerPlayerState.ACTIVE, "2C", "3D");
        PokerPlayer lowerUserId = player(1, 3, 997, 3, PokerPlayerState.ACTIVE, "4C", "5D");
        GameState game = showdown(
                List.of(lowerUserId, dealerFolded, clockwiseFirst), 1,
                "AS", "KS", "QS", "JS", "TS");

        PotAward award = engine.settleShowdown(game).potAwards().getFirst();

        assertThat(award.potAmount()).isEqualTo(9);
        assertThat(award.baseShare()).isEqualTo(4);
        assertThat(award.oddChipUserIds()).containsExactly(99L);
        assertThat(award.winnerPayouts()).containsEntry(99L, 5L).containsEntry(1L, 4L);
    }

    @Test
    void synthetic101ChipAwardUsesTheSameClockwiseOddChipRule() {
        PokerPlayer dealer = player(50, 1, 1_000, 0, PokerPlayerState.FOLDED);
        PokerPlayer clockwiseFirst = player(99, 2, 1_000, 0, PokerPlayerState.ACTIVE);
        PokerPlayer lowerUserId = player(1, 3, 1_000, 0, PokerPlayerState.ACTIVE);
        GameState game = state(GamePhase.PRE_FLOP, List.of(lowerUserId, dealer, clockwiseFirst), 1, List.of());
        Pot pot = new Pot(PotType.MAIN, 0, 101, 1, List.of(50L, 99L, 1L), List.of(99L, 1L));

        PotAward award = engine.calculateAward(
                game, pot, List.of(99L, 1L),
                Optional.of(new HandValue(HandCategory.ROYAL_FLUSH, List.of())));

        assertThat(award.baseShare()).isEqualTo(50);
        assertThat(award.oddChipUserIds()).containsExactly(99L);
        assertThat(award.winnerPayouts()).containsEntry(99L, 51L).containsEntry(1L, 50L);
    }

    @Test
    void mainAndMultipleSidePotsResolveIndependentlyWithDifferentWinners() {
        PokerPlayer a = player(1, 1, 900, 100, PokerPlayerState.ALL_IN, "KH", "KD");
        PokerPlayer b = player(2, 2, 700, 300, PokerPlayerState.ALL_IN, "9H", "9D");
        PokerPlayer c = player(3, 3, 500, 500, PokerPlayerState.ACTIVE, "7C", "8C");
        PokerPlayer d = player(4, 4, 500, 500, PokerPlayerState.ACTIVE, "AC", "AD");
        GameState game = showdown(List.of(a, b, c, d), 4, "KC", "9S", "7H", "3D", "2S");

        HandSettlementResult result = engine.settleShowdown(game);

        assertThat(result.potAwards()).extracting(PotAward::potType)
                .containsExactly(PotType.MAIN, PotType.SIDE, PotType.SIDE);
        assertThat(result.potAwards()).extracting(PotAward::potAmount)
                .containsExactly(400L, 600L, 400L);
        assertThat(result.potAwards()).extracting(award -> award.winnerUserIds().getFirst())
                .containsExactly(1L, 2L, 4L);
        assertThat(a.tableChips()).isEqualTo(1_300);
        assertThat(b.tableChips()).isEqualTo(1_300);
        assertThat(c.tableChips()).isEqualTo(500);
        assertThat(d.tableChips()).isEqualTo(900);
        assertConserved(result);
    }

    @Test
    void tiedSidePotSplitsOnlyAmongItsEligiblePlayersAndAwardsOddChipClockwise() {
        PokerPlayer mainWinner = player(1, 1, 900, 100, PokerPlayerState.ALL_IN, "6H", "KH");
        PokerPlayer sideFirst = player(90, 2, 699, 301, PokerPlayerState.ACTIVE, "AH", "KD");
        PokerPlayer sideSecond = player(2, 3, 699, 301, PokerPlayerState.ACTIVE, "AS", "QD");
        PokerPlayer folded = player(4, 4, 699, 301, PokerPlayerState.FOLDED);
        GameState game = showdown(
                List.of(folded, sideSecond, mainWinner, sideFirst), 1,
                "2C", "3D", "4H", "5S", "9C");

        HandSettlementResult result = engine.settleShowdown(game);
        PotAward main = result.potAwards().get(0);
        PotAward side = result.potAwards().get(1);

        assertThat(main.winnerUserIds()).containsExactly(1L);
        assertThat(side.potAmount()).isEqualTo(603);
        assertThat(side.winnerUserIds()).containsExactly(90L, 2L);
        assertThat(side.winnerPayouts()).containsEntry(90L, 302L).containsEntry(2L, 301L);
        assertThat(side.oddChipUserIds()).containsExactly(90L);
        assertThat(side.winnerPayouts()).doesNotContainKey(1L);
        assertConserved(result);
    }

    @Test
    void foldedAndLeavingContributorsAreNotEvaluatedWhileDisconnectedPlayersCanWin() {
        PokerPlayer disconnected = player(1, 1, 900, 100, PokerPlayerState.ALL_IN, "AH", "AD");
        disconnected.markDisconnected();
        PokerPlayer folded = player(2, 2, 900, 100, PokerPlayerState.FOLDED);
        PokerPlayer leaving = new PokerPlayer(
                3, 3, 900, 0, 100, PokerPlayerState.FOLDED, List.of(), false, true);
        GameState game = showdown(List.of(disconnected, folded, leaving), 2, "KC", "9S", "7H", "3D", "2C");

        HandSettlementResult result = engine.settleShowdown(game);

        assertThat(result.evaluatedHands()).isEmpty();
        assertThat(result.potAwards().getFirst().winnerUserIds()).containsExactly(1L);
        assertThat(disconnected.tableChips()).isEqualTo(1_200);
        assertConserved(result);
    }

    @Test
    void disconnectedActivePlayerRemainsEligibleAndCanWin() {
        PokerPlayer disconnected = player(1, 1, 900, 100, PokerPlayerState.ACTIVE, "AH", "AD");
        disconnected.markDisconnected();
        PokerPlayer connected = player(2, 2, 900, 100, PokerPlayerState.ACTIVE, "KH", "QD");

        HandSettlementResult result = engine.settleShowdown(
                showdown(List.of(disconnected, connected), 2, "2C", "3D", "7H", "9S", "JC"));

        assertThat(result.potAwards().getFirst().winnerUserIds()).containsExactly(1L);
        assertThat(disconnected.tableChips()).isEqualTo(1_100);
    }

    @Test
    void uncalledExcessIsReturnedOnceAndNeverIncludedInPotAward() {
        PokerPlayer first = player(1, 1, 900, 100, PokerPlayerState.ALL_IN, "AH", "AD");
        PokerPlayer second = player(2, 2, 700, 300, PokerPlayerState.ALL_IN, "KH", "KD");
        PokerPlayer third = player(3, 3, 500, 500, PokerPlayerState.ACTIVE, "QH", "QD");
        GameState game = showdown(List.of(first, second, third), 2, "2C", "3D", "7H", "9S", "JC");

        HandSettlementResult result = engine.settleShowdown(game);

        assertThat(result.potAwards()).extracting(PotAward::potAmount).containsExactly(300L, 400L);
        assertThat(result.uncalledBetReturns()).containsExactly(new UncalledBetReturn(3, 200));
        assertThat(third.tableChips()).isEqualTo(700);
        assertThat(result.totalPayout()).isEqualTo(900);
        assertConserved(result);
    }

    @Test
    void foldOnlyPreFlopSettlementNeedsNoCardsAndAwardsSoleContender() {
        PokerPlayer first = player(1, 1, 900, 100, PokerPlayerState.FOLDED);
        PokerPlayer second = player(2, 2, 900, 100, PokerPlayerState.FOLDED);
        PokerPlayer winner = player(3, 3, 900, 100, PokerPlayerState.ACTIVE);
        GameState game = state(GamePhase.PRE_FLOP, List.of(first, second, winner), 1, List.of());
        game.finishByFolds();

        HandSettlementResult result = engine.settleByFolds(game);

        assertThat(result.foldOnly()).isTrue();
        assertThat(result.evaluatedHands()).isEmpty();
        assertThat(result.potAwards().getFirst().winningHandValue()).isEmpty();
        assertThat(result.potAwards().getFirst().winnerUserIds()).containsExactly(3L);
        assertThat(winner.tableChips()).isEqualTo(1_200);
        assertConserved(result);
    }

    @Test
    void oneEligibleShowdownPlayerIsAwardedDirectlyWithoutHoleCards() {
        PokerPlayer folded = player(1, 1, 900, 100, PokerPlayerState.FOLDED);
        PokerPlayer winner = player(2, 2, 900, 100, PokerPlayerState.ACTIVE);
        GameState game = showdown(List.of(folded, winner), 1, "2C", "3D", "7H", "9S", "JC");

        HandSettlementResult result = engine.settleShowdown(game);

        assertThat(result.evaluatedHands()).isEmpty();
        assertThat(result.potAwards().getFirst().winningHandValue()).isEmpty();
        assertThat(result.potAwards().getFirst().winnerUserIds()).containsExactly(2L);
        assertThat(winner.tableChips()).isEqualTo(1_100);
    }

    @Test
    void foldOnlyUncalledCommitmentIsReturnedWhenNoContestedPotExists() {
        PokerPlayer folded = player(1, 1, 1_000, 0, PokerPlayerState.FOLDED);
        PokerPlayer winner = player(2, 2, 900, 100, PokerPlayerState.ACTIVE);
        GameState game = state(GamePhase.PRE_FLOP, List.of(folded, winner), 1, List.of());
        game.finishByFolds();

        HandSettlementResult result = engine.settleByFolds(game);

        assertThat(result.potAwards()).isEmpty();
        assertThat(result.uncalledBetReturns()).containsExactly(new UncalledBetReturn(2, 100));
        assertThat(winner.tableChips()).isEqualTo(1_000);
        assertConserved(result);
    }

    @Test
    void zeroEligiblePotFailsBeforeAnyMutation() {
        PokerPlayer first = player(1, 1, 900, 100, PokerPlayerState.FOLDED);
        PokerPlayer second = player(2, 2, 900, 100, PokerPlayerState.FOLDED);
        GameState game = state(GamePhase.PRE_FLOP, List.of(first, second), 1, List.of());
        game.finishByFolds();

        assertThatThrownBy(() -> engine.settleByFolds(game))
                .isInstanceOf(IllegalStateException.class);
        assertThat(first.tableChips()).isEqualTo(900);
        assertThat(first.totalCommitted()).isEqualTo(100);
        assertThat(game.isFinanciallySettled()).isFalse();
    }

    @Test
    void secondSettlementIsRejectedWithoutChangingChips() {
        PokerPlayer winner = player(1, 1, 900, 100, PokerPlayerState.ACTIVE, "AH", "AD");
        PokerPlayer loser = player(2, 2, 900, 100, PokerPlayerState.ACTIVE, "KH", "QD");
        GameState game = showdown(List.of(winner, loser), 2, "2C", "3D", "7H", "9S", "JC");
        engine.settleShowdown(game);
        long winnerChips = winner.tableChips();
        long loserChips = loser.tableChips();

        assertThatThrownBy(() -> engine.settleShowdown(game))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already");
        assertThat(winner.tableChips()).isEqualTo(winnerChips);
        assertThat(loser.tableChips()).isEqualTo(loserChips);
    }

    @Test
    void resultCollectionsAndMapsAreImmutable() {
        PokerPlayer first = player(1, 1, 900, 100, PokerPlayerState.ACTIVE, "AH", "AD");
        PokerPlayer second = player(2, 2, 900, 100, PokerPlayerState.ACTIVE, "KH", "QD");
        HandSettlementResult result = engine.settleShowdown(
                showdown(List.of(first, second), 2, "2C", "3D", "7H", "9S", "JC"));
        PotAward award = result.potAwards().getFirst();

        assertThatThrownBy(() -> result.potAwards().add(award)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.evaluatedHands().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.totalCredits().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> award.winnerUserIds().add(9L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> award.winnerPayouts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void payoutOverflowFailsBeforeMutation() {
        PokerPlayer richWinner = player(1, 1, Long.MAX_VALUE, 1, PokerPlayerState.ACTIVE, "AH", "AD");
        PokerPlayer loser = player(2, 2, 0, 1, PokerPlayerState.ACTIVE, "KH", "QD");
        GameState game = showdown(List.of(richWinner, loser), 2, "2C", "3D", "7H", "9S", "JC");

        assertThatThrownBy(() -> engine.settleShowdown(game)).isInstanceOf(ArithmeticException.class);
        assertThat(richWinner.tableChips()).isEqualTo(Long.MAX_VALUE);
        assertThat(richWinner.totalCommitted()).isEqualTo(1);
        assertThat(loser.totalCommitted()).isEqualTo(1);
        assertThat(game.isFinanciallySettled()).isFalse();
    }

    private void assertWinnerCategory(
            List<String> board,
            List<String> firstCards,
            List<String> secondCards,
            long expectedWinner,
            HandCategory expectedCategory) {
        PokerPlayer first = player(1, 1, 900, 100, PokerPlayerState.ACTIVE,
                firstCards.get(0), firstCards.get(1));
        PokerPlayer second = player(2, 2, 900, 100, PokerPlayerState.ACTIVE,
                secondCards.get(0), secondCards.get(1));
        PotAward award = engine.settleShowdown(showdown(
                List.of(first, second), 2, board.toArray(String[]::new))).potAwards().getFirst();
        assertThat(award.winnerUserIds()).containsExactly(expectedWinner);
        assertThat(award.winningHandValue().orElseThrow().category()).isEqualTo(expectedCategory);
    }

    private static GameState showdown(
            List<PokerPlayer> players,
            int dealer,
            String... board) {
        return state(GamePhase.SHOWDOWN, players, dealer, cards(board));
    }

    private static GameState state(
            GamePhase phase,
            List<PokerPlayer> players,
            int dealer,
            List<Card> board) {
        int smallBlind = players.stream().mapToInt(PokerPlayer::seatNumber)
                .filter(seat -> seat != dealer).findFirst().orElse(dealer);
        int bigBlind = players.stream().mapToInt(PokerPlayer::seatNumber)
                .filter(seat -> seat != dealer && seat != smallBlind).findFirst().orElse(smallBlind);
        return new GameState(
                GAME_ID, HAND_ID, phase, dealer, smallBlind, bigBlind, null,
                0, 100, board, players, Duration.ZERO, 0, null);
    }

    private static PokerPlayer player(
            long userId,
            int seat,
            long tableChips,
            long totalCommitted,
            PokerPlayerState state,
            String... holeCards) {
        return new PokerPlayer(userId, seat, tableChips, 0, totalCommitted, state, cards(holeCards));
    }

    private static List<Card> cards(String... codes) {
        return java.util.Arrays.stream(codes).map(HandSettlementEngineTests::card).toList();
    }

    private static Card card(String code) {
        Rank rank = switch (code.charAt(0)) {
            case '2' -> Rank.TWO;
            case '3' -> Rank.THREE;
            case '4' -> Rank.FOUR;
            case '5' -> Rank.FIVE;
            case '6' -> Rank.SIX;
            case '7' -> Rank.SEVEN;
            case '8' -> Rank.EIGHT;
            case '9' -> Rank.NINE;
            case 'T' -> Rank.TEN;
            case 'J' -> Rank.JACK;
            case 'Q' -> Rank.QUEEN;
            case 'K' -> Rank.KING;
            case 'A' -> Rank.ACE;
            default -> throw new IllegalArgumentException("unknown rank: " + code);
        };
        Suit suit = switch (code.charAt(1)) {
            case 'C' -> Suit.CLUBS;
            case 'D' -> Suit.DIAMONDS;
            case 'H' -> Suit.HEARTS;
            case 'S' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("unknown suit: " + code);
        };
        return new Card(rank, suit);
    }

    private static void assertConserved(HandSettlementResult result) {
        assertThat(result.chipsAfterSettlement()).isEqualTo(result.chipsBeforeSettlement());
    }
}
