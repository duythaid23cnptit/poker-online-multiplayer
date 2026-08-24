package com.ptit.poker.game.domain.state;

import com.ptit.poker.game.domain.betting.BettingEngine;
import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Deck;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import com.ptit.poker.game.domain.round.BettingRoundState;
import com.ptit.poker.game.domain.round.PokerRoundEngine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PokerPlayerOrthogonalityTests {

    private static final UUID GAME_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HAND_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void activePlayerDisconnectPreservesParticipationAndPotEligibility() {
        PokerPlayer player = player(10, 1, 500, 20, 80, PokerPlayerState.ACTIVE);

        player.markDisconnected();

        assertThat(player.playerState()).isEqualTo(PokerPlayerState.ACTIVE);
        assertThat(player.isDisconnected()).isTrue();
        assertThat(player.isEligibleToWin()).isTrue();
        assertThat(player.canReceiveBettingTurn()).isTrue();
        assertThat(player.canAcceptClientBettingAction()).isFalse();
        assertThat(player.totalCommitted()).isEqualTo(80);
    }

    @Test
    void allInPlayerDisconnectPreservesAllInAndEligibilityWithoutFutureTurn() {
        PokerPlayer allIn = player(10, 1, 0, 100, 100, PokerPlayerState.ALL_IN);

        allIn.markDisconnected();

        assertThat(allIn.playerState()).isEqualTo(PokerPlayerState.ALL_IN);
        assertThat(allIn.isDisconnected()).isTrue();
        assertThat(allIn.isEligibleToWin()).isTrue();
        assertThat(allIn.canReceiveBettingTurn()).isFalse();

        PokerPlayer second = player(20, 2, 500, 100, 100, PokerPlayerState.ACTIVE);
        PokerPlayer third = player(30, 3, 500, 100, 100, PokerPlayerState.ACTIVE);
        GameState game = game(List.of(allIn, second, third), 100);
        BettingRoundState round = roundEngine().start(game, new Deck());

        assertThat(round.pendingResponses()).containsExactlyInAnyOrder(20L, 30L);
        assertThat(game.currentTurnUserId()).isEqualTo(20);
    }

    @Test
    void foldedPlayerDisconnectPreservesFoldAndCommittedChips() {
        PokerPlayer player = player(10, 1, 400, 50, 150, PokerPlayerState.FOLDED);

        player.markDisconnected();

        assertThat(player.playerState()).isEqualTo(PokerPlayerState.FOLDED);
        assertThat(player.isDisconnected()).isTrue();
        assertThat(player.isEligibleToWin()).isFalse();
        assertThat(player.canReceiveBettingTurn()).isFalse();
        assertThat(player.currentBet()).isEqualTo(50);
        assertThat(player.totalCommitted()).isEqualTo(150);
    }

    @Test
    void leavingDuringActiveHandFoldsPlayerButPreservesCommitmentsAndConnectivityFact() {
        PokerPlayer player = player(10, 1, 400, 50, 150, PokerPlayerState.ACTIVE);
        player.markDisconnected();

        player.markLeaving();

        assertThat(player.playerState()).isEqualTo(PokerPlayerState.FOLDED);
        assertThat(player.isLeaving()).isTrue();
        assertThat(player.isDisconnected()).isTrue();
        assertThat(player.isEligibleToWin()).isFalse();
        assertThat(player.canReceiveBettingTurn()).isFalse();
        assertThat(player.tableChips()).isEqualTo(400);
        assertThat(player.currentBet()).isEqualTo(50);
        assertThat(player.totalCommitted()).isEqualTo(150);
    }

    @Test
    void disconnectedActivePlayerRetainsOfficialTurnButHasNoNormalClientActions() {
        PokerPlayer disconnected = player(20, 2, 500, 0, 0, PokerPlayerState.ACTIVE);
        disconnected.markDisconnected();
        PokerPlayer dealer = player(10, 1, 500, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer third = player(30, 3, 500, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = game(List.of(dealer, disconnected, third), 0);
        PokerRoundEngine roundEngine = roundEngine();
        BettingRoundState round = roundEngine.start(game, new Deck());

        assertThat(game.currentTurnUserId()).isEqualTo(20);
        assertThat(round.pendingResponses()).contains(20L);
        assertThat(new BettingEngine().legalActions(game, 20).actions()).isEmpty();
        assertThat(roundEngine.legalActions(game, round, 20).actions()).isEmpty();
    }

    @Test
    void reconnectRestoresClientActionEligibilityWithoutChangingParticipation() {
        PokerPlayer player = player(10, 1, 500, 0, 0, PokerPlayerState.ACTIVE);
        player.markDisconnected();

        player.markConnected();

        assertThat(player.playerState()).isEqualTo(PokerPlayerState.ACTIVE);
        assertThat(player.isConnected()).isTrue();
        assertThat(player.canAcceptClientBettingAction()).isTrue();
    }

    private PokerRoundEngine roundEngine() {
        AtomicLong sequence = new AtomicLong(500);
        return new PokerRoundEngine(new BettingEngine(), () -> new UUID(0, sequence.incrementAndGet()));
    }

    private static GameState game(List<PokerPlayer> players, long currentBet) {
        return new GameState(
                GAME_ID,
                HAND_ID,
                GamePhase.FLOP,
                1,
                2,
                3,
                null,
                currentBet,
                100,
                0,
                List.of(
                        new Card(Rank.ACE, Suit.SPADES),
                        new Card(Rank.KING, Suit.HEARTS),
                        new Card(Rank.QUEEN, Suit.DIAMONDS)),
                players,
                Duration.ofSeconds(30),
                0,
                null,
                100);
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
}
