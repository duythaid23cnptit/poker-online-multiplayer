package com.ptit.poker.game.domain.betting;

import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BettingEngineTests {

    private static final UUID GAME_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HAND_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TURN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final long ACTOR_ID = 10;

    private final BettingEngine engine = new BettingEngine();

    @Test
    void exposesExactlyTheApprovedActionTypes() {
        assertThat(PokerActionType.values()).containsExactly(
                PokerActionType.FOLD,
                PokerActionType.CHECK,
                PokerActionType.CALL,
                PokerActionType.BET,
                PokerActionType.RAISE,
                PokerActionType.ALL_IN);
    }

    @Test
    void activePlayerCanFoldWithoutChangingCommittedChipsAndCannotActAgain() {
        PokerPlayer player = player(ACTOR_ID, 1, 900, 40, 100, PokerPlayerState.ACTIVE);
        GameState game = game(player, 40, 20, TURN_ID, ACTOR_ID);

        BettingResult result = engine.apply(game, BettingAction.fold(ACTOR_ID, TURN_ID));

        assertThat(result.amountCommitted()).isZero();
        assertThat(player.playerState()).isEqualTo(PokerPlayerState.FOLDED);
        assertThat(player.tableChips()).isEqualTo(900);
        assertThat(player.currentBet()).isEqualTo(40);
        assertThat(player.totalCommitted()).isEqualTo(100);
        assertThat(engine.legalActions(game, ACTOR_ID).actions()).isEmpty();
        assertThatThrownBy(() -> engine.apply(game, BettingAction.check(ACTOR_ID, TURN_ID)))
                .isInstanceOf(BettingRuleViolationException.class);
    }

    @Test
    void checkIsLegalOnlyWhenNoChipsAreOwedAndChangesNoAccounting() {
        PokerPlayer player = player(ACTOR_ID, 1, 900, 40, 100, PokerPlayerState.ACTIVE);
        GameState game = game(player, 40, 20, TURN_ID, ACTOR_ID);

        LegalActions legal = engine.legalActions(game, ACTOR_ID);
        BettingResult result = engine.apply(game, BettingAction.check(ACTOR_ID, TURN_ID));

        assertThat(legal.actions()).contains(PokerActionType.CHECK).doesNotContain(PokerActionType.CALL);
        assertThat(result.amountCommitted()).isZero();
        assertThat(player.tableChips()).isEqualTo(900);
        assertThat(player.currentBet()).isEqualTo(40);
        assertThat(player.totalCommitted()).isEqualTo(100);
        assertThat(game.currentBet()).isEqualTo(40);

        GameState owing = game(player(ACTOR_ID, 1, 900, 20, 100, PokerPlayerState.ACTIVE),
                40, 20, TURN_ID, ACTOR_ID);
        assertThatThrownBy(() -> engine.apply(owing, BettingAction.check(ACTOR_ID, TURN_ID)))
                .isInstanceOf(BettingRuleViolationException.class);
    }

    @Test
    void callCommitsTheExactDifferenceOnceAndUpdatesBothPlayerCommitments() {
        PokerPlayer player = player(ACTOR_ID, 1, 100, 10, 30, PokerPlayerState.ACTIVE);
        GameState game = game(player, 30, 20, TURN_ID, ACTOR_ID);

        BettingResult result = engine.apply(game, BettingAction.call(ACTOR_ID, TURN_ID));

        assertThat(result.amountCommitted()).isEqualTo(20);
        assertThat(player.tableChips()).isEqualTo(80);
        assertThat(player.currentBet()).isEqualTo(30);
        assertThat(player.totalCommitted()).isEqualTo(50);
        assertThat(game.currentBet()).isEqualTo(30);
        assertThat(game.mainPot()).isZero();
    }

    @Test
    void callIsIllegalWhenNothingIsOwedOrFullCallIsUnaffordable() {
        PokerPlayer matched = player(ACTOR_ID, 1, 100, 30, 30, PokerPlayerState.ACTIVE);
        GameState matchedGame = game(matched, 30, 20, TURN_ID, ACTOR_ID);
        PokerPlayer shortPlayer = player(ACTOR_ID, 1, 10, 20, 20, PokerPlayerState.ACTIVE);
        GameState shortGame = game(shortPlayer, 50, 20, TURN_ID, ACTOR_ID);

        assertThat(engine.legalActions(matchedGame, ACTOR_ID).actions())
                .contains(PokerActionType.CHECK).doesNotContain(PokerActionType.CALL);
        assertThat(engine.legalActions(shortGame, ACTOR_ID).actions())
                .contains(PokerActionType.ALL_IN, PokerActionType.FOLD).doesNotContain(PokerActionType.CALL);
        assertThatThrownBy(() -> engine.apply(shortGame, BettingAction.call(ACTOR_ID, TURN_ID)))
                .isInstanceOf(BettingRuleViolationException.class);
    }

    @Test
    void normalBetUsesTargetSemanticsAndUpdatesCurrentBetAndMinimumRaise() {
        PokerPlayer player = player(ACTOR_ID, 1, 200, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = game(player, 0, 20, TURN_ID, ACTOR_ID);

        BettingResult result = engine.apply(game, BettingAction.betTo(ACTOR_ID, TURN_ID, 60));

        assertThat(result.amountCommitted()).isEqualTo(60);
        assertThat(result.fullRaise()).isTrue();
        assertThat(player.tableChips()).isEqualTo(140);
        assertThat(player.currentBet()).isEqualTo(60);
        assertThat(player.totalCommitted()).isEqualTo(60);
        assertThat(game.currentBet()).isEqualTo(60);
        assertThat(game.minimumRaise()).isEqualTo(60);
    }

    @Test
    void betIsRejectedAfterExistingWagerBelowMinimumAndAboveAvailableChips() {
        GameState existing = game(player(ACTOR_ID, 1, 200, 0, 0, PokerPlayerState.ACTIVE),
                20, 20, TURN_ID, ACTOR_ID);
        GameState noWager = game(player(ACTOR_ID, 1, 50, 0, 0, PokerPlayerState.ACTIVE),
                0, 20, TURN_ID, ACTOR_ID);

        assertThat(engine.legalActions(existing, ACTOR_ID).actions()).doesNotContain(PokerActionType.BET);
        assertThatThrownBy(() -> engine.apply(existing, BettingAction.betTo(ACTOR_ID, TURN_ID, 40)))
                .isInstanceOf(BettingRuleViolationException.class);
        assertThatThrownBy(() -> engine.apply(noWager, BettingAction.betTo(ACTOR_ID, TURN_ID, 10)))
                .isInstanceOf(BettingRuleViolationException.class).hasMessageContaining("minimum");
        assertThatThrownBy(() -> engine.apply(noWager, BettingAction.betTo(ACTOR_ID, TURN_ID, 60)))
                .isInstanceOf(BettingRuleViolationException.class).hasMessageContaining("available");
    }

    @Test
    void raiseUsesResultingStreetBetTargetAndCommitsOnlyTheDifference() {
        PokerPlayer player = player(ACTOR_ID, 1, 200, 40, 60, PokerPlayerState.ACTIVE);
        GameState game = game(player, 100, 50, TURN_ID, ACTOR_ID);

        BettingResult result = engine.apply(game, BettingAction.raiseTo(ACTOR_ID, TURN_ID, 150));

        assertThat(result.amountCommitted()).isEqualTo(110);
        assertThat(player.tableChips()).isEqualTo(90);
        assertThat(player.currentBet()).isEqualTo(150);
        assertThat(player.totalCommitted()).isEqualTo(170);
        assertThat(game.currentBet()).isEqualTo(150);
        assertThat(game.minimumRaise()).isEqualTo(50);
    }

    @Test
    void largerFullRaiseSetsItsSizeAsTheNextMinimumRaise() {
        PokerPlayer player = player(ACTOR_ID, 1, 300, 40, 40, PokerPlayerState.ACTIVE);
        GameState game = game(player, 100, 40, TURN_ID, ACTOR_ID);

        engine.apply(game, BettingAction.raiseTo(ACTOR_ID, TURN_ID, 160));

        assertThat(game.currentBet()).isEqualTo(160);
        assertThat(game.minimumRaise()).isEqualTo(60);
    }

    @Test
    void raiseIsRejectedWithoutExistingBetBelowMinimumOrAboveAvailableChips() {
        GameState noWager = game(player(ACTOR_ID, 1, 300, 0, 0, PokerPlayerState.ACTIVE),
                0, 40, TURN_ID, ACTOR_ID);
        GameState belowMinimum = game(player(ACTOR_ID, 1, 200, 40, 40, PokerPlayerState.ACTIVE),
                100, 50, TURN_ID, ACTOR_ID);
        GameState aboveAvailable = game(player(ACTOR_ID, 1, 100, 40, 40, PokerPlayerState.ACTIVE),
                100, 50, TURN_ID, ACTOR_ID);

        assertThat(engine.legalActions(noWager, ACTOR_ID).actions()).doesNotContain(PokerActionType.RAISE);
        assertThatThrownBy(() -> engine.apply(belowMinimum, BettingAction.raiseTo(ACTOR_ID, TURN_ID, 149)))
                .isInstanceOf(BettingRuleViolationException.class).hasMessageContaining("minimum");
        assertThatThrownBy(() -> engine.apply(aboveAvailable, BettingAction.raiseTo(ACTOR_ID, TURN_ID, 200)))
                .isInstanceOf(BettingRuleViolationException.class);
    }

    @Test
    void allInCommitsEveryRemainingChipAndMarksPlayerAllIn() {
        PokerPlayer player = player(ACTOR_ID, 1, 75, 20, 30, PokerPlayerState.ACTIVE);
        GameState game = game(player, 20, 20, TURN_ID, ACTOR_ID);

        BettingResult result = engine.apply(game, BettingAction.allIn(ACTOR_ID, TURN_ID));

        assertThat(result.amountCommitted()).isEqualTo(75);
        assertThat(result.allIn()).isTrue();
        assertThat(player.tableChips()).isZero();
        assertThat(player.currentBet()).isEqualTo(95);
        assertThat(player.totalCommitted()).isEqualTo(105);
        assertThat(player.playerState()).isEqualTo(PokerPlayerState.ALL_IN);
    }

    @Test
    void allInBelowCallDoesNotLowerGameCurrentBet() {
        PokerPlayer player = player(ACTOR_ID, 1, 50, 20, 20, PokerPlayerState.ACTIVE);
        GameState game = game(player, 100, 50, TURN_ID, ACTOR_ID);

        engine.apply(game, BettingAction.allIn(ACTOR_ID, TURN_ID));

        assertThat(player.currentBet()).isEqualTo(70);
        assertThat(game.currentBet()).isEqualTo(100);
        assertThat(game.minimumRaise()).isEqualTo(50);
    }

    @Test
    void exactCallAllInMatchesCurrentBetWithoutChangingMinimumRaise() {
        PokerPlayer player = player(ACTOR_ID, 1, 60, 40, 40, PokerPlayerState.ACTIVE);
        GameState game = game(player, 100, 50, TURN_ID, ACTOR_ID);

        BettingResult result = engine.apply(game, BettingAction.allIn(ACTOR_ID, TURN_ID));

        assertThat(player.currentBet()).isEqualTo(100);
        assertThat(game.currentBet()).isEqualTo(100);
        assertThat(game.minimumRaise()).isEqualTo(50);
        assertThat(result.fullRaise()).isFalse();
    }

    @Test
    void fullRaiseAllInUpdatesCurrentBetAndMinimumRaise() {
        PokerPlayer player = player(ACTOR_ID, 1, 160, 40, 40, PokerPlayerState.ACTIVE);
        GameState game = game(player, 100, 50, TURN_ID, ACTOR_ID);

        BettingResult result = engine.apply(game, BettingAction.allIn(ACTOR_ID, TURN_ID));

        assertThat(game.currentBet()).isEqualTo(200);
        assertThat(game.minimumRaise()).isEqualTo(100);
        assertThat(result.fullRaise()).isTrue();
    }

    @Test
    void shortAllInRaiseIncreasesCurrentBetWithoutReducingMinimumRaise() {
        PokerPlayer player = player(ACTOR_ID, 1, 90, 40, 40, PokerPlayerState.ACTIVE);
        GameState game = game(player, 100, 50, TURN_ID, ACTOR_ID);

        BettingResult result = engine.apply(game, BettingAction.allIn(ACTOR_ID, TURN_ID));

        assertThat(player.currentBet()).isEqualTo(130);
        assertThat(game.currentBet()).isEqualTo(130);
        assertThat(game.minimumRaise()).isEqualTo(50);
        assertThat(result.fullRaise()).isFalse();
    }

    @Test
    void shortOpeningAllInBelowMinimumIsLegalButNormalBetIsNot() {
        PokerPlayer player = player(ACTOR_ID, 1, 10, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = game(player, 0, 20, TURN_ID, ACTOR_ID);

        assertThat(engine.legalActions(game, ACTOR_ID).actions())
                .contains(PokerActionType.ALL_IN).doesNotContain(PokerActionType.BET);
        engine.apply(game, BettingAction.allIn(ACTOR_ID, TURN_ID));

        assertThat(game.currentBet()).isEqualTo(10);
        assertThat(game.minimumRaise()).isEqualTo(20);
    }

    @Test
    void zeroChipPlayerCannotAllIn() {
        PokerPlayer player = player(ACTOR_ID, 1, 0, 20, 20, PokerPlayerState.ACTIVE);
        GameState game = game(player, 20, 20, TURN_ID, ACTOR_ID);

        assertThat(engine.legalActions(game, ACTOR_ID).actions()).doesNotContain(PokerActionType.ALL_IN);
        assertThatThrownBy(() -> engine.apply(game, BettingAction.allIn(ACTOR_ID, TURN_ID)))
                .isInstanceOf(BettingRuleViolationException.class);
    }

    @Test
    void legalActionsReflectWhetherAStreetWagerExistsAndCallAffordability() {
        GameState noBet = game(player(ACTOR_ID, 1, 100, 0, 0, PokerPlayerState.ACTIVE),
                0, 20, TURN_ID, ACTOR_ID);
        GameState owes = game(player(ACTOR_ID, 1, 100, 20, 20, PokerPlayerState.ACTIVE),
                50, 20, TURN_ID, ACTOR_ID);

        assertThat(engine.legalActions(noBet, ACTOR_ID).actions())
                .contains(PokerActionType.FOLD, PokerActionType.CHECK, PokerActionType.BET, PokerActionType.ALL_IN)
                .doesNotContain(PokerActionType.CALL, PokerActionType.RAISE);
        assertThat(engine.legalActions(owes, ACTOR_ID).actions())
                .contains(PokerActionType.FOLD, PokerActionType.CALL, PokerActionType.RAISE, PokerActionType.ALL_IN)
                .doesNotContain(PokerActionType.CHECK, PokerActionType.BET);
        assertThat(engine.legalActions(owes, ACTOR_ID).callAmount()).isEqualTo(30);
    }

    @Test
    void foldedAllInLeavingAndDisconnectedPlayersHaveNoClientActions() {
        for (PokerPlayerState state : List.of(
                PokerPlayerState.FOLDED,
                PokerPlayerState.ALL_IN,
                PokerPlayerState.LEAVING,
                PokerPlayerState.DISCONNECTED)) {
            PokerPlayer player = player(ACTOR_ID, 1, state == PokerPlayerState.ALL_IN ? 0 : 100,
                    20, 20, state);
            assertThat(engine.legalActions(game(player, 20, 20, TURN_ID, ACTOR_ID), ACTOR_ID).actions())
                    .as("state %s", state)
                    .isEmpty();
        }
    }

    @Test
    void onlyCurrentPlayerWithMatchingTurnIdCanAct() {
        PokerPlayer actor = player(ACTOR_ID, 1, 100, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = game(actor, 0, 20, TURN_ID, ACTOR_ID);
        UUID stale = UUID.fromString("40000000-0000-0000-0000-000000000001");

        assertThat(engine.legalActions(game, 20).actions()).isEmpty();
        assertThatThrownBy(() -> engine.apply(game, BettingAction.check(20, TURN_ID)))
                .isInstanceOf(BettingRuleViolationException.class).hasMessageContaining("current player");
        assertThatThrownBy(() -> engine.apply(game, BettingAction.check(ACTOR_ID, stale)))
                .isInstanceOf(BettingRuleViolationException.class).hasMessageContaining("stale");
        assertThatThrownBy(() -> engine.apply(game, BettingAction.check(ACTOR_ID, null)))
                .isInstanceOf(BettingRuleViolationException.class).hasMessageContaining("stale");
        assertThat(engine.apply(game, BettingAction.check(ACTOR_ID, TURN_ID)).actionType())
                .isEqualTo(PokerActionType.CHECK);
    }

    @Test
    void noCurrentTurnMeansNoLegalAction() {
        PokerPlayer player = player(ACTOR_ID, 1, 100, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = game(player, 0, 20, null, null);

        assertThat(engine.legalActions(game, ACTOR_ID).actions()).isEmpty();
        assertThatThrownBy(() -> engine.apply(game, BettingAction.check(ACTOR_ID, TURN_ID)))
                .isInstanceOf(BettingRuleViolationException.class).hasMessageContaining("no current turn");
    }

    @Test
    void acceptedActionAdvancesVersionButDoesNotAdvanceTurnOrStreet() {
        PokerPlayer player = player(ACTOR_ID, 1, 100, 0, 0, PokerPlayerState.ACTIVE);
        GameState game = game(player, 0, 20, TURN_ID, ACTOR_ID);

        engine.apply(game, BettingAction.check(ACTOR_ID, TURN_ID));

        assertThat(game.stateVersion()).isEqualTo(1);
        assertThat(game.currentTurnUserId()).isEqualTo(ACTOR_ID);
        assertThat(game.turnId()).isEqualTo(TURN_ID);
        assertThat(game.phase()).isEqualTo(GamePhase.PRE_FLOP);
        assertThat(game.communityCards()).isEmpty();
    }

    @Test
    void commitChipsRejectsNegativeExcessAndArithmeticOverflowAtomically() {
        PokerPlayer ordinary = player(ACTOR_ID, 1, 100, 10, 20, PokerPlayerState.ACTIVE);
        assertThatThrownBy(() -> ordinary.commitChips(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ordinary.commitChips(101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ordinary.tableChips()).isEqualTo(100);
        assertThat(ordinary.currentBet()).isEqualTo(10);
        assertThat(ordinary.totalCommitted()).isEqualTo(20);

        PokerPlayer currentBetOverflow = player(
                ACTOR_ID, 1, 1, Long.MAX_VALUE, 0, PokerPlayerState.ACTIVE);
        assertThatThrownBy(() -> currentBetOverflow.commitChips(1)).isInstanceOf(ArithmeticException.class);
        assertThat(currentBetOverflow.tableChips()).isEqualTo(1);
        assertThat(currentBetOverflow.currentBet()).isEqualTo(Long.MAX_VALUE);

        PokerPlayer committedOverflow = player(
                ACTOR_ID, 1, 1, 0, Long.MAX_VALUE, PokerPlayerState.ACTIVE);
        assertThatThrownBy(() -> committedOverflow.commitChips(1)).isInstanceOf(ArithmeticException.class);
        assertThat(committedOverflow.tableChips()).isEqualTo(1);
        assertThat(committedOverflow.totalCommitted()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void accountingAcrossBetThenLaterCallDeductsEachWagerExactlyOnce() {
        PokerPlayer player = player(ACTOR_ID, 1, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        GameState openingBetState = game(player, 0, 20, TURN_ID, ACTOR_ID);

        engine.apply(openingBetState, BettingAction.betTo(ACTOR_ID, TURN_ID, 100));
        assertThat(player.tableChips()).isEqualTo(900);
        assertThat(player.totalCommitted()).isEqualTo(100);

        GameState laterCallState = game(player, 150, 50, TURN_ID, ACTOR_ID);
        engine.apply(laterCallState, BettingAction.call(ACTOR_ID, TURN_ID));

        assertThat(player.tableChips()).isEqualTo(850);
        assertThat(player.currentBet()).isEqualTo(150);
        assertThat(player.totalCommitted()).isEqualTo(150);
        assertThat(laterCallState.mainPot()).isZero();
    }

    @Test
    void actionTargetFieldIsOnlyAcceptedForBetAndRaise() {
        assertThatThrownBy(() -> new BettingAction(ACTOR_ID, TURN_ID, PokerActionType.CALL, 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only valid");
        assertThat(BettingAction.raiseTo(ACTOR_ID, TURN_ID, 150).targetCurrentBet()).isEqualTo(150);
    }

    @Test
    void legalActionCollectionIsImmutable() {
        LegalActions actions = engine.legalActions(
                game(player(ACTOR_ID, 1, 100, 0, 0, PokerPlayerState.ACTIVE),
                        0, 20, TURN_ID, ACTOR_ID),
                ACTOR_ID);

        assertThatThrownBy(() -> actions.actions().add(PokerActionType.CALL))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static GameState game(
            PokerPlayer actor,
            long currentBet,
            long minimumRaise,
            UUID turnId,
            Long currentTurnUserId) {
        PokerPlayer second = player(20, 2, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        PokerPlayer third = player(30, 3, 1_000, 0, 0, PokerPlayerState.ACTIVE);
        return new GameState(
                GAME_ID,
                HAND_ID,
                GamePhase.PRE_FLOP,
                1,
                2,
                3,
                currentTurnUserId,
                currentBet,
                minimumRaise,
                0,
                List.of(),
                List.of(actor, second, third),
                Duration.ofSeconds(30),
                0,
                turnId);
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
