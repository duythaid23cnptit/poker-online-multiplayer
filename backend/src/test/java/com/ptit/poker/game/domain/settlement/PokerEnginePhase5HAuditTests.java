package com.ptit.poker.game.domain.settlement;

import com.ptit.poker.game.domain.betting.BettingAction;
import com.ptit.poker.game.domain.betting.BettingEngine;
import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Deck;
import com.ptit.poker.game.domain.hand.HandCategory;
import com.ptit.poker.game.domain.hand.HandEvaluator;
import com.ptit.poker.game.domain.hand.HandValue;
import com.ptit.poker.game.domain.pot.Pot;
import com.ptit.poker.game.domain.pot.PotType;
import com.ptit.poker.game.domain.pot.UncalledBetReturn;
import com.ptit.poker.game.domain.round.BettingRoundState;
import com.ptit.poker.game.domain.round.PokerRoundEngine;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokerEnginePhase5HAuditTests {

    private static final UUID GAME_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HAND_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void maximumTableDealHas23UniqueCardsAndLeaves29InDeck() {
        Deck deck = new Deck();
        List<Card> holeCards = deck.draw(18);
        List<PokerPlayer> players = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            players.add(new PokerPlayer(
                    index + 1L, index + 1, 1_000, 0, 0, PokerPlayerState.ACTIVE,
                    holeCards.subList(index * 2, index * 2 + 2)));
        }
        List<Card> board = deck.draw(5);

        GameState game = state(GamePhase.SHOWDOWN, players, 1, 2, 3, 0, board);
        List<Card> allDealt = new ArrayList<>(board);
        game.players().forEach(player -> allDealt.addAll(player.holeCards()));

        assertThat(allDealt).hasSize(23);
        assertThat(new HashSet<>(allDealt)).hasSize(23);
        assertThat(deck.remainingCount()).isEqualTo(29);
    }

    @Test
    void completeFourPlayerHandFlowsAcrossEveryStreetAndSettlesExactlyOnce() {
        Deck deck = new Deck();
        List<Card> dealtHoleCards = deck.draw(8);
        PokerPlayer dealer = dealtPlayer(1, 1, 1_000, 0, 0, dealtHoleCards, 0);
        PokerPlayer smallBlind = dealtPlayer(2, 2, 950, 50, 50, dealtHoleCards, 2);
        PokerPlayer shortBigBlind = dealtPlayer(3, 3, 300, 100, 100, dealtHoleCards, 4);
        PokerPlayer fourth = dealtPlayer(4, 4, 1_000, 0, 0, dealtHoleCards, 6);
        GameState game = state(
                GamePhase.PRE_FLOP,
                List.of(dealer, smallBlind, shortBigBlind, fourth),
                1, 2, 3, 100, List.of());
        AtomicLong turnSequence = new AtomicLong();
        PokerRoundEngine rounds = new PokerRoundEngine(
                new BettingEngine(), () -> new UUID(0, turnSequence.incrementAndGet()));
        BettingRoundState round = rounds.start(game, deck);

        act(rounds, game, round, deck, BettingAction.call(4, game.turnId()));
        act(rounds, game, round, deck, BettingAction.raiseTo(1, game.turnId(), 200));
        act(rounds, game, round, deck, BettingAction.fold(2, game.turnId()));
        act(rounds, game, round, deck, BettingAction.call(3, game.turnId()));
        act(rounds, game, round, deck, BettingAction.call(4, game.turnId()));
        assertThat(game.phase()).isEqualTo(GamePhase.FLOP);
        assertStreetAccounting(game, 0, 200, 50, 200, 200);

        act(rounds, game, round, deck, BettingAction.check(3, game.turnId()));
        act(rounds, game, round, deck, BettingAction.betTo(4, game.turnId(), 100));
        act(rounds, game, round, deck, BettingAction.call(1, game.turnId()));
        act(rounds, game, round, deck, BettingAction.call(3, game.turnId()));
        assertThat(game.phase()).isEqualTo(GamePhase.TURN);
        assertStreetAccounting(game, 0, 300, 50, 300, 300);

        act(rounds, game, round, deck, BettingAction.allIn(3, game.turnId()));
        act(rounds, game, round, deck, BettingAction.call(4, game.turnId()));
        act(rounds, game, round, deck, BettingAction.call(1, game.turnId()));
        assertThat(game.phase()).isEqualTo(GamePhase.RIVER);
        assertThat(shortBigBlind.playerState()).isEqualTo(PokerPlayerState.ALL_IN);
        assertStreetAccounting(game, 0, 400, 50, 400, 400);

        act(rounds, game, round, deck, BettingAction.check(4, game.turnId()));
        act(rounds, game, round, deck, BettingAction.check(1, game.turnId()));
        assertThat(game.phase()).isEqualTo(GamePhase.SHOWDOWN);
        assertThat(game.communityCards()).hasSize(5);
        assertThat(game.currentTurnUserId()).isNull();
        assertThat(game.turnId()).isNull();

        List<Card> knownCards = new ArrayList<>(game.communityCards());
        game.players().forEach(player -> knownCards.addAll(player.holeCards()));
        assertThat(new HashSet<>(knownCards)).hasSize(knownCards.size());
        long chipsBeforeHand = systemChips(game);

        HandSettlementEngine settlement = new HandSettlementEngine(new HandEvaluator());
        HandSettlementResult result = settlement.settleShowdown(game);

        assertThat(result.potAwards()).extracting(PotAward::potAmount).containsExactly(200L, 1_050L);
        assertThat(game.phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(game.currentTurnUserId()).isNull();
        assertThat(game.turnId()).isNull();
        assertThat(game.players()).allSatisfy(player -> {
            assertThat(player.currentBet()).isZero();
            assertThat(player.totalCommitted()).isZero();
        });
        assertThat(totalTableChips(game)).isEqualTo(chipsBeforeHand);
        List<Long> settledStacks = game.players().stream().map(PokerPlayer::tableChips).toList();
        assertThatThrownBy(() -> settlement.settleShowdown(game)).isInstanceOf(IllegalStateException.class);
        assertThat(game.players()).extracting(PokerPlayer::tableChips).containsExactlyElementsOf(settledStacks);
    }

    @Test
    void shortAllInFoldedContributorAndDisconnectedWinnerProduceIndependentPots() {
        PokerPlayer shortAllIn = player(1, 1, 900, 100, PokerPlayerState.ALL_IN, "KH", "KD");
        PokerPlayer middleAllIn = player(2, 2, 700, 300, PokerPlayerState.ALL_IN, "9H", "9D");
        PokerPlayer foldedDeep = player(3, 3, 500, 500, PokerPlayerState.FOLDED, "7C", "8C");
        PokerPlayer disconnectedDeep = player(4, 4, 500, 500, PokerPlayerState.ACTIVE, "AC", "AD");
        disconnectedDeep.markDisconnected();
        GameState game = state(
                GamePhase.SHOWDOWN,
                List.of(shortAllIn, middleAllIn, foldedDeep, disconnectedDeep),
                4, 1, 2, 0, cards("KC", "9S", "7H", "3D", "2S"));
        long initialSystemChips = systemChips(game);

        HandSettlementResult result = new HandSettlementEngine(new HandEvaluator()).settleShowdown(game);

        assertThat(result.potAwards()).extracting(PotAward::potAmount)
                .containsExactly(400L, 600L, 400L);
        assertThat(result.potAwards()).extracting(award -> award.winnerUserIds().getFirst())
                .containsExactly(1L, 2L, 4L);
        assertThat(result.evaluatedHands()).doesNotContainKey(3L);
        assertThat(disconnectedDeep.tableChips()).isEqualTo(900);
        assertThat(totalTableChips(game)).isEqualTo(initialSystemChips);
    }

    @Test
    void earlyFoldHandPaysContestedPotAndUncalledExcessWithoutBoardRunout() {
        PokerPlayer firstFolded = player(1, 1, 900, 100, PokerPlayerState.FOLDED);
        PokerPlayer secondFolded = player(2, 2, 900, 100, PokerPlayerState.FOLDED);
        PokerPlayer soleContender = player(3, 3, 700, 300, PokerPlayerState.ACTIVE);
        GameState game = state(
                GamePhase.PRE_FLOP,
                List.of(firstFolded, secondFolded, soleContender),
                1, 2, 3, 300, List.of());
        long initialSystemChips = systemChips(game);
        game.finishByFolds();

        HandSettlementEngine settlement = new HandSettlementEngine(new HandEvaluator());
        HandSettlementResult result = settlement.settleByFolds(game);

        assertThat(result.foldOnly()).isTrue();
        assertThat(result.evaluatedHands()).isEmpty();
        assertThat(result.potAwards()).extracting(PotAward::potAmount).containsExactly(300L);
        assertThat(result.uncalledBetReturns()).containsExactly(new UncalledBetReturn(3, 200));
        assertThat(soleContender.tableChips()).isEqualTo(1_200);
        assertThat(game.communityCards()).isEmpty();
        assertThat(totalTableChips(game)).isEqualTo(initialSystemChips);
        long settledChips = soleContender.tableChips();
        assertThatThrownBy(() -> settlement.settleByFolds(game)).isInstanceOf(IllegalStateException.class);
        assertThat(soleContender.tableChips()).isEqualTo(settledChips);
    }

    @Test
    void oddChipOrderHandlesSeatGapsAndWrapsAfterDealer() {
        PokerPlayer lowSeat = player(1, 2, 1_000, 0, PokerPlayerState.ACTIVE);
        PokerPlayer dealer = player(8, 8, 1_000, 0, PokerPlayerState.FOLDED);
        PokerPlayer firstAfterDealer = player(99, 9, 1_000, 0, PokerPlayerState.ACTIVE);
        GameState game = state(
                GamePhase.PRE_FLOP, List.of(lowSeat, dealer, firstAfterDealer),
                8, 9, 2, 0, List.of());
        Pot syntheticOddPot = new Pot(
                PotType.MAIN, 0, 101, 1,
                List.of(1L, 8L, 99L), List.of(1L, 99L));
        HandSettlementEngine settlement = new HandSettlementEngine(new HandEvaluator());

        PotAward award = settlement.calculateAward(
                game, syntheticOddPot, List.of(1L, 99L),
                Optional.of(new HandValue(HandCategory.ROYAL_FLUSH, List.of())));

        assertThat(award.winnerUserIds()).containsExactly(99L, 1L);
        assertThat(award.oddChipUserIds()).containsExactly(99L);
        assertThat(award.winnerPayouts()).containsEntry(99L, 51L).containsEntry(1L, 50L);
    }

    private static void act(
            PokerRoundEngine engine,
            GameState game,
            BettingRoundState round,
            Deck deck,
            BettingAction action) {
        engine.act(game, round, action, deck);
    }

    private static void assertStreetAccounting(GameState game, long currentBet, long... commitments) {
        assertThat(game.currentBet()).isEqualTo(currentBet);
        assertThat(game.players()).allSatisfy(player -> assertThat(player.currentBet()).isZero());
        assertThat(game.players()).extracting(PokerPlayer::totalCommitted)
                .containsExactly(java.util.Arrays.stream(commitments).boxed().toArray(Long[]::new));
    }

    private static PokerPlayer dealtPlayer(
            long userId,
            int seat,
            long tableChips,
            long currentBet,
            long totalCommitted,
            List<Card> dealt,
            int offset) {
        return new PokerPlayer(
                userId, seat, tableChips, currentBet, totalCommitted, PokerPlayerState.ACTIVE,
                dealt.subList(offset, offset + 2));
    }

    private static GameState state(
            GamePhase phase,
            List<PokerPlayer> players,
            int dealer,
            int smallBlind,
            int bigBlind,
            long currentBet,
            List<Card> board) {
        return new GameState(
                GAME_ID, HAND_ID, phase, dealer, smallBlind, bigBlind, null,
                currentBet, 100, board, players, Duration.ZERO, 0, null);
    }

    private static long systemChips(GameState game) {
        long total = 0;
        for (PokerPlayer player : game.players()) {
            total = Math.addExact(total, Math.addExact(player.tableChips(), player.totalCommitted()));
        }
        return total;
    }

    private static long totalTableChips(GameState game) {
        long total = 0;
        for (PokerPlayer player : game.players()) {
            total = Math.addExact(total, player.tableChips());
        }
        return total;
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
        return java.util.Arrays.stream(codes).map(PokerEnginePhase5HAuditTests::card).toList();
    }

    private static Card card(String code) {
        return new Card(
                switch (code.charAt(0)) {
                    case '2' -> com.ptit.poker.game.domain.card.Rank.TWO;
                    case '3' -> com.ptit.poker.game.domain.card.Rank.THREE;
                    case '7' -> com.ptit.poker.game.domain.card.Rank.SEVEN;
                    case '8' -> com.ptit.poker.game.domain.card.Rank.EIGHT;
                    case '9' -> com.ptit.poker.game.domain.card.Rank.NINE;
                    case 'K' -> com.ptit.poker.game.domain.card.Rank.KING;
                    case 'A' -> com.ptit.poker.game.domain.card.Rank.ACE;
                    default -> throw new IllegalArgumentException("unknown rank: " + code);
                },
                switch (code.charAt(1)) {
                    case 'C' -> com.ptit.poker.game.domain.card.Suit.CLUBS;
                    case 'D' -> com.ptit.poker.game.domain.card.Suit.DIAMONDS;
                    case 'H' -> com.ptit.poker.game.domain.card.Suit.HEARTS;
                    case 'S' -> com.ptit.poker.game.domain.card.Suit.SPADES;
                    default -> throw new IllegalArgumentException("unknown suit: " + code);
                });
    }
}
