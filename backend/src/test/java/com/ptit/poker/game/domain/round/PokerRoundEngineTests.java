package com.ptit.poker.game.domain.round;

import com.ptit.poker.game.domain.betting.BettingAction;
import com.ptit.poker.game.domain.betting.BettingEngine;
import com.ptit.poker.game.domain.betting.BettingRuleViolationException;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Deck;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokerRoundEngineTests {

    private static final UUID GAME_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HAND_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private final AtomicLong turnSequence = new AtomicLong(100);
    private final PokerRoundEngine engine = new PokerRoundEngine(
            new BettingEngine(), () -> new UUID(0, turnSequence.incrementAndGet()));

    @Test
    void preFlopStartsAfterBigBlindAndHandlesSeatGapsAndWraparound() {
        List<PokerPlayer> players = List.of(
                player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(30, 3, 900, 100, 100, PokerPlayerState.ACTIVE),
                player(60, 6, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(80, 8, 1_000, 0, 0, PokerPlayerState.ACTIVE));
        GameState game = game(GamePhase.PRE_FLOP, players, 1, 6, 3, 100, 100, List.of());

        BettingRoundState round = engine.start(game, new Deck());
        assertThat(game.requirePlayer(game.currentTurnUserId()).seatNumber()).isEqualTo(6);

        UUID firstTurn = game.turnId();
        engine.act(game, round, BettingAction.call(60, firstTurn), new Deck());
        assertThat(game.requirePlayer(game.currentTurnUserId()).seatNumber()).isEqualTo(8);
        engine.act(game, round, BettingAction.call(80, game.turnId()), new Deck());
        assertThat(game.requirePlayer(game.currentTurnUserId()).seatNumber()).isEqualTo(1);
    }

    @Test
    void turnSelectionSkipsFoldedAllInAndLeavingButPreservesDisconnectedTurn() {
        PokerPlayer leaving = player(40, 4, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        leaving.markLeaving();
        PokerPlayer disconnected = player(60, 6, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        disconnected.markDisconnected();
        List<PokerPlayer> players = List.of(
                player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(20, 2, 1_000, 0, 0, PokerPlayerState.FOLDED),
                player(30, 3, 0, 0, 100, PokerPlayerState.ALL_IN),
                leaving,
                disconnected);
        GameState game = game(GamePhase.FLOP, players, 1, 2, 3, 0, 100, flopBoard());

        BettingRoundState round = engine.start(game, new Deck());

        assertThat(game.currentTurnUserId()).isEqualTo(60);
        assertThat(round.pendingResponses()).containsExactlyInAnyOrder(10L, 60L);
        assertThat(engine.legalActions(game, round, 60).actions()).isEmpty();
    }

    @Test
    void preFlopBlindSequenceGivesBigBlindItsOptionThenDealsFlopAndResetsStreet() {
        PokerPlayer dealer = player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer smallBlind = player(20, 2, 950, 50, 50, PokerPlayerState.ACTIVE);
        PokerPlayer bigBlind = player(30, 3, 900, 100, 100, PokerPlayerState.ACTIVE);
        PokerPlayer fourth = player(40, 4, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = game(
                GamePhase.PRE_FLOP,
                List.of(dealer, smallBlind, bigBlind, fourth),
                1, 2, 3, 100, 100, List.of());
        Deck deck = new Deck();
        BettingRoundState round = engine.start(game, deck);

        engine.act(game, round, BettingAction.call(40, game.turnId()), deck);
        engine.act(game, round, BettingAction.call(10, game.turnId()), deck);
        engine.act(game, round, BettingAction.call(20, game.turnId()), deck);
        RoundTransitionResult result = engine.act(game, round, BettingAction.check(30, game.turnId()), deck);

        assertThat(result.bettingRoundCompleted()).isTrue();
        assertThat(result.streetAdvanced()).isTrue();
        assertThat(game.phase()).isEqualTo(GamePhase.FLOP);
        assertThat(game.communityCards()).hasSize(3);
        assertThat(result.communityCardsDealt()).containsExactlyElementsOf(game.communityCards());
        assertThat(deck.remainingCount()).isEqualTo(49);
        assertThat(game.currentBet()).isZero();
        assertThat(game.minimumRaise()).isEqualTo(100);
        assertThat(game.players()).allSatisfy(player -> assertThat(player.currentBet()).isZero());
        assertThat(game.players()).extracting(PokerPlayer::totalCommitted)
                .containsExactly(100L, 100L, 100L, 100L);
        assertThat(game.currentTurnUserId()).isEqualTo(20);
        assertThat(result.stateVersion()).isEqualTo(5);
    }

    @Test
    void postFlopCheckAroundCompletesExactlyAfterLastRequiredPlayer() {
        GameState game = postFlopGame(threeActivePlayers(), 0, 100);
        Deck deck = deckPastExistingBoard();
        BettingRoundState round = engine.start(game, deck);

        RoundTransitionResult first = engine.act(game, round, BettingAction.check(20, game.turnId()), deck);
        assertThat(first.bettingRoundCompleted()).isFalse();
        assertThat(game.currentTurnUserId()).isEqualTo(30);
        RoundTransitionResult second = engine.act(game, round, BettingAction.check(30, game.turnId()), deck);
        assertThat(second.bettingRoundCompleted()).isFalse();
        assertThat(game.currentTurnUserId()).isEqualTo(10);
        RoundTransitionResult third = engine.act(game, round, BettingAction.check(10, game.turnId()), deck);

        assertThat(third.bettingRoundCompleted()).isTrue();
        assertThat(game.phase()).isEqualTo(GamePhase.TURN);
        assertThat(game.communityCards()).hasSize(4);
        assertThat(third.communityCardsDealt()).hasSize(1);
    }

    @Test
    void laterBetReopensActionForEarlierCheckersAndEqualBetsAloneDoNotFinish() {
        GameState game = postFlopGame(threeActivePlayers(), 0, 100);
        Deck deck = deckPastExistingBoard();
        BettingRoundState round = engine.start(game, deck);

        engine.act(game, round, BettingAction.check(20, game.turnId()), deck);
        engine.act(game, round, BettingAction.check(30, game.turnId()), deck);
        RoundTransitionResult bet = engine.act(game, round, BettingAction.betTo(10, game.turnId(), 100), deck);

        assertThat(bet.bettingRoundCompleted()).isFalse();
        assertThat(round.pendingResponses()).containsExactlyInAnyOrder(20L, 30L);
        assertThat(game.currentTurnUserId()).isEqualTo(20);
        engine.act(game, round, BettingAction.call(20, game.turnId()), deck);
        assertThat(game.phase()).isEqualTo(GamePhase.FLOP);
        RoundTransitionResult finalCall = engine.act(game, round, BettingAction.call(30, game.turnId()), deck);
        assertThat(finalCall.bettingRoundCompleted()).isTrue();
        assertThat(game.phase()).isEqualTo(GamePhase.TURN);
    }

    @Test
    void fullRaiseReopensEveryPriorCallerUntilAllRespondAgain() {
        List<PokerPlayer> players = fourActivePlayers();
        GameState game = postFlopGame(players, 0, 100);
        Deck deck = deckPastExistingBoard();
        BettingRoundState round = engine.start(game, deck);

        engine.act(game, round, BettingAction.betTo(20, game.turnId(), 100), deck);
        engine.act(game, round, BettingAction.call(30, game.turnId()), deck);
        engine.act(game, round, BettingAction.call(40, game.turnId()), deck);
        RoundTransitionResult raised = engine.act(game, round, BettingAction.raiseTo(10, game.turnId(), 250), deck);

        assertThat(raised.bettingResult().fullRaise()).isTrue();
        assertThat(round.pendingResponses()).containsExactlyInAnyOrder(20L, 30L, 40L);
        assertThat(game.currentTurnUserId()).isEqualTo(20);
        engine.act(game, round, BettingAction.call(20, game.turnId()), deck);
        engine.act(game, round, BettingAction.call(30, game.turnId()), deck);
        RoundTransitionResult last = engine.act(game, round, BettingAction.call(40, game.turnId()), deck);
        assertThat(last.bettingRoundCompleted()).isTrue();
    }

    @Test
    void shortAllInRequiresAdditionalCallsWithoutReopeningRaiseRights() {
        PokerPlayer dealer = player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer first = player(20, 2, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer second = player(30, 3, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer shortStack = player(40, 4, 150, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = postFlopGame(List.of(dealer, first, second, shortStack), 0, 100);
        Deck deck = deckPastExistingBoard();
        BettingRoundState round = engine.start(game, deck);

        engine.act(game, round, BettingAction.betTo(20, game.turnId(), 100), deck);
        engine.act(game, round, BettingAction.call(30, game.turnId()), deck);
        engine.act(game, round, BettingAction.allIn(40, game.turnId()), deck);
        engine.act(game, round, BettingAction.fold(10, game.turnId()), deck);

        assertThat(game.currentBet()).isEqualTo(150);
        assertThat(game.minimumRaise()).isEqualTo(100);
        assertThat(round.pendingResponses()).containsExactlyInAnyOrder(20L, 30L);
        assertThat(engine.legalActions(game, round, 20).actions())
                .contains(PokerActionType.CALL)
                .doesNotContain(PokerActionType.RAISE, PokerActionType.ALL_IN);
        engine.act(game, round, BettingAction.call(20, game.turnId()), deck);
        RoundTransitionResult last = engine.act(game, round, BettingAction.call(30, game.turnId()), deck);
        assertThat(last.bettingRoundCompleted()).isTrue();
    }

    @Test
    void cumulativeShortAllInsReopenRaiseRightsAtAFullRaiseIncrement() {
        PokerPlayer dealerShort = player(10, 1, 200, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer first = player(20, 2, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer second = player(30, 3, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer thirdShort = player(40, 4, 150, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = postFlopGame(List.of(dealerShort, first, second, thirdShort), 0, 100);
        Deck deck = deckPastExistingBoard();
        BettingRoundState round = engine.start(game, deck);

        engine.act(game, round, BettingAction.betTo(20, game.turnId(), 100), deck);
        engine.act(game, round, BettingAction.call(30, game.turnId()), deck);
        engine.act(game, round, BettingAction.allIn(40, game.turnId()), deck);
        assertThat(game.currentBet()).isEqualTo(150);
        engine.act(game, round, BettingAction.allIn(10, game.turnId()), deck);

        assertThat(game.currentBet()).isEqualTo(200);
        assertThat(game.minimumRaise()).isEqualTo(100);
        assertThat(engine.legalActions(game, round, 20).actions()).contains(PokerActionType.RAISE);
    }

    @Test
    void allInPlayersAreSkippedAndNoFurtherDecisionRunsBoardToShowdown() {
        List<PokerPlayer> players = List.of(
                player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(20, 2, 0, 100, 100, PokerPlayerState.ALL_IN),
                player(30, 3, 0, 100, 100, PokerPlayerState.ALL_IN));
        GameState game = game(GamePhase.PRE_FLOP, players, 1, 2, 3, 100, 100, List.of());
        Deck deck = new Deck();

        BettingRoundState round = engine.start(game, deck);

        assertThat(game.phase()).isEqualTo(GamePhase.SHOWDOWN);
        assertThat(game.communityCards()).hasSize(5).doesNotHaveDuplicates();
        assertThat(deck.remainingCount()).isEqualTo(47);
        assertThat(game.currentTurnUserId()).isNull();
        assertThat(game.turnId()).isNull();
        assertThat(round.phase()).isEqualTo(GamePhase.SHOWDOWN);
    }

    @Test
    void foldLeavingOneContenderEndsHandWithoutShowdownOrPotMutation() {
        List<PokerPlayer> players = List.of(
                player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(20, 2, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(30, 3, 900, 100, 100, PokerPlayerState.FOLDED));
        GameState game = game(GamePhase.PRE_FLOP, players, 1, 2, 3, 100, 100, List.of());
        Deck deck = new Deck();
        BettingRoundState round = engine.start(game, deck);

        RoundTransitionResult result = engine.act(
                game, round, BettingAction.fold(game.currentTurnUserId(), game.turnId()), deck);

        assertThat(result.handDecidedByFold()).isTrue();
        assertThat(game.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(game.currentTurnUserId()).isNull();
        assertThat(game.turnId()).isNull();
        assertThat(game.mainPot()).isZero();
        assertThat(game.communityCards()).isEmpty();
    }

    @Test
    void everyStreetDealsOnlyRequiredCardsAndRiverTransitionsWithoutSixthCard() {
        GameState game = postFlopGame(threeActivePlayers(), 0, 100);
        Deck deck = deckPastExistingBoard();
        BettingRoundState round = engine.start(game, deck);

        checkAround(game, round, deck);
        assertThat(game.phase()).isEqualTo(GamePhase.TURN);
        assertThat(game.communityCards()).hasSize(4);
        checkAround(game, round, deck);
        assertThat(game.phase()).isEqualTo(GamePhase.RIVER);
        assertThat(game.communityCards()).hasSize(5);
        int remainingBeforeShowdown = deck.remainingCount();
        checkAround(game, round, deck);

        assertThat(game.phase()).isEqualTo(GamePhase.SHOWDOWN);
        assertThat(game.communityCards()).hasSize(5).doesNotHaveDuplicates();
        assertThat(deck.remainingCount()).isEqualTo(remainingBeforeShowdown);
        assertThat(game.currentTurnUserId()).isNull();
        assertThat(game.turnId()).isNull();
    }

    @Test
    void eachNewActorGetsNewTurnIdAndPreviousTokenImmediatelyBecomesStale() {
        GameState game = postFlopGame(threeActivePlayers(), 0, 100);
        Deck deck = deckPastExistingBoard();
        BettingRoundState round = engine.start(game, deck);
        UUID firstTurnId = game.turnId();

        engine.act(game, round, BettingAction.check(20, firstTurnId), deck);

        assertThat(game.currentTurnUserId()).isEqualTo(30);
        assertThat(game.turnId()).isNotEqualTo(firstTurnId);
        assertThatThrownBy(() -> engine.act(game, round, BettingAction.check(20, firstTurnId), deck))
                .isInstanceOf(BettingRuleViolationException.class);
        assertThatThrownBy(() -> engine.act(game, round, BettingAction.check(30, firstTurnId), deck))
                .isInstanceOf(BettingRuleViolationException.class).hasMessageContaining("stale");
    }

    @Test
    void wrongUserAndSamePlayerTwiceAreRejectedWithoutStateMutation() {
        GameState game = postFlopGame(threeActivePlayers(), 0, 100);
        Deck deck = deckPastExistingBoard();
        BettingRoundState round = engine.start(game, deck);
        long version = game.stateVersion();

        assertThatThrownBy(() -> engine.act(game, round, BettingAction.check(30, game.turnId()), deck))
                .isInstanceOf(BettingRuleViolationException.class);
        assertThat(game.stateVersion()).isEqualTo(version);
        UUID firstTurn = game.turnId();
        engine.act(game, round, BettingAction.check(20, firstTurn), deck);
        assertThatThrownBy(() -> engine.act(game, round, BettingAction.check(20, firstTurn), deck))
                .isInstanceOf(BettingRuleViolationException.class);
    }

    @Test
    void realisticFourSeatSequencePreservesCommitmentsAndDoesNotPrematurelyComplete() {
        PokerPlayer dealer = player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer smallBlind = player(20, 2, 950, 50, 50, PokerPlayerState.ACTIVE);
        PokerPlayer bigBlind = player(30, 3, 900, 100, 100, PokerPlayerState.ACTIVE);
        PokerPlayer fourth = player(40, 4, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = game(GamePhase.PRE_FLOP, List.of(dealer, smallBlind, bigBlind, fourth),
                1, 2, 3, 100, 100, List.of());
        Deck deck = new Deck();
        BettingRoundState round = engine.start(game, deck);

        engine.act(game, round, BettingAction.call(40, game.turnId()), deck);
        engine.act(game, round, BettingAction.call(10, game.turnId()), deck);
        engine.act(game, round, BettingAction.call(20, game.turnId()), deck);
        engine.act(game, round, BettingAction.check(30, game.turnId()), deck);
        assertThat(game.phase()).isEqualTo(GamePhase.FLOP);

        engine.act(game, round, BettingAction.check(20, game.turnId()), deck);
        engine.act(game, round, BettingAction.check(30, game.turnId()), deck);
        engine.act(game, round, BettingAction.betTo(40, game.turnId(), 100), deck);
        engine.act(game, round, BettingAction.fold(10, game.turnId()), deck);
        RoundTransitionResult smallBlindCall = engine.act(
                game, round, BettingAction.call(20, game.turnId()), deck);

        assertThat(smallBlindCall.bettingRoundCompleted()).isFalse();
        assertThat(game.currentTurnUserId()).isEqualTo(30);
        RoundTransitionResult bigBlindResponse = engine.act(
                game, round, BettingAction.call(30, game.turnId()), deck);
        assertThat(bigBlindResponse.bettingRoundCompleted()).isTrue();
        assertThat(game.phase()).isEqualTo(GamePhase.TURN);
        assertThat(game.players()).extracting(PokerPlayer::totalCommitted)
                .containsExactly(100L, 200L, 200L, 200L);
        assertThat(game.players()).allSatisfy(player -> assertThat(player.currentBet()).isZero());
        assertThat(game.mainPot()).isZero();
    }

    private void checkAround(GameState game, BettingRoundState round, Deck deck) {
        while (game.currentTurnUserId() != null) {
            GamePhase phaseBefore = game.phase();
            engine.act(game, round, BettingAction.check(game.currentTurnUserId(), game.turnId()), deck);
            if (game.phase() != phaseBefore) {
                return;
            }
        }
    }

    private static GameState postFlopGame(List<PokerPlayer> players, long currentBet, long minimumRaise) {
        return game(GamePhase.FLOP, players, 1, 2, 3, currentBet, minimumRaise, flopBoard());
    }

    private static GameState game(
            GamePhase phase,
            List<PokerPlayer> players,
            int dealer,
            int smallBlind,
            int bigBlind,
            long currentBet,
            long minimumRaise,
            List<Card> board) {
        return new GameState(
                GAME_ID,
                HAND_ID,
                phase,
                dealer,
                smallBlind,
                bigBlind,
                null,
                currentBet,
                minimumRaise,
                0,
                board,
                players,
                Duration.ofSeconds(30),
                0,
                null,
                100);
    }

    private static List<PokerPlayer> threeActivePlayers() {
        return List.of(
                player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(20, 2, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(30, 3, 1_000, 0, 0, PokerPlayerState.ACTIVE));
    }

    private static List<PokerPlayer> fourActivePlayers() {
        return List.of(
                player(10, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(20, 2, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(30, 3, 1_000, 0, 0, PokerPlayerState.ACTIVE),
                player(40, 4, 1_000, 0, 0, PokerPlayerState.ACTIVE));
    }

    private static PokerPlayer player(
            long userId,
            int seat,
            long tableChips,
            long currentBet,
            long totalCommitted,
            PokerPlayerState state) {
        return new PokerPlayer(userId, seat, tableChips, currentBet, totalCommitted, state, List.of());
    }

    private static List<Card> flopBoard() {
        return List.of(
                new Card(Rank.ACE, Suit.SPADES),
                new Card(Rank.KING, Suit.HEARTS),
                new Card(Rank.QUEEN, Suit.DIAMONDS));
    }

    private static Deck deckPastExistingBoard() {
        return new Deck();
    }
}
