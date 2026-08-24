package com.ptit.poker.game.domain.betting;

import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;
import com.ptit.poker.game.domain.state.PokerPlayerState;

import java.util.EnumSet;
import java.util.Objects;

/** Applies server-authoritative wager rules without advancing turns or streets. */
public final class BettingEngine {

    public LegalActions legalActions(GameState gameState, long userId) {
        Objects.requireNonNull(gameState, "gameState must not be null");
        if (gameState.currentTurnUserId() == null || gameState.currentTurnUserId() != userId) {
            return LegalActions.none();
        }

        PokerPlayer player;
        try {
            player = gameState.requirePlayer(userId);
        } catch (IllegalArgumentException exception) {
            return LegalActions.none();
        }
        if (!player.canAcceptClientBettingAction()) {
            return LegalActions.none();
        }

        EnumSet<PokerActionType> actions = EnumSet.of(PokerActionType.FOLD);
        long callAmount = Math.subtractExact(gameState.currentBet(), player.currentBet());
        long maximumCommit = maximumCommittable(player);
        long maximumTarget = Math.addExact(player.currentBet(), maximumCommit);
        boolean canCommitAll = player.tableChips() > 0 && maximumCommit == player.tableChips();

        if (callAmount == 0) {
            actions.add(PokerActionType.CHECK);
        } else if (callAmount <= maximumCommit) {
            actions.add(PokerActionType.CALL);
        }
        if (canCommitAll) {
            actions.add(PokerActionType.ALL_IN);
        }

        long minimumTarget = minimumTarget(gameState);
        if (gameState.currentBet() == 0) {
            if (maximumTarget >= minimumTarget && maximumCommit > 0) {
                actions.add(PokerActionType.BET);
            }
        } else if (maximumTarget >= minimumTarget) {
            actions.add(PokerActionType.RAISE);
        }
        return new LegalActions(actions, callAmount, minimumTarget, maximumTarget);
    }

    public BettingResult apply(GameState gameState, BettingAction action) {
        Objects.requireNonNull(gameState, "gameState must not be null");
        Objects.requireNonNull(action, "action must not be null");
        validateTurn(gameState, action);
        if (gameState.stateVersion() == Long.MAX_VALUE) {
            throw new BettingRuleViolationException("stateVersion cannot advance");
        }

        PokerPlayer player;
        try {
            player = gameState.requirePlayer(action.userId());
        } catch (IllegalArgumentException exception) {
            throw new BettingRuleViolationException("action user is not a player in the game");
        }
        LegalActions legalActions = legalActions(gameState, action.userId());
        if (!legalActions.allows(action.type())) {
            throw new BettingRuleViolationException(action.type() + " is not legal for the current state");
        }

        BettingResult result = switch (action.type()) {
            case FOLD -> fold(player);
            case CHECK -> unchangedResult(PokerActionType.CHECK, player);
            case CALL -> call(gameState, player, legalActions.callAmount());
            case BET -> bet(gameState, player, action.targetCurrentBet(), legalActions);
            case RAISE -> raise(gameState, player, action.targetCurrentBet(), legalActions);
            case ALL_IN -> allIn(gameState, player);
        };
        gameState.advanceStateVersion();
        return result;
    }

    private static BettingResult fold(PokerPlayer player) {
        player.markFolded();
        return unchangedResult(PokerActionType.FOLD, player);
    }

    private static BettingResult call(GameState gameState, PokerPlayer player, long callAmount) {
        player.commitChips(callAmount);
        return new BettingResult(
                PokerActionType.CALL,
                player.userId(),
                callAmount,
                player.currentBet(),
                player.playerState() == PokerPlayerState.ALL_IN,
                false);
    }

    private static BettingResult bet(
            GameState gameState,
            PokerPlayer player,
            long target,
            LegalActions legalActions) {
        if (target < legalActions.minimumTarget()) {
            throw new BettingRuleViolationException("BET target is below the minimum bet");
        }
        if (target > legalActions.maximumTarget()) {
            throw new BettingRuleViolationException("BET target exceeds available chips");
        }
        long amount = Math.subtractExact(target, player.currentBet());
        player.commitChips(amount);
        gameState.recordOpeningBet(target);
        return new BettingResult(
                PokerActionType.BET,
                player.userId(),
                amount,
                target,
                player.playerState() == PokerPlayerState.ALL_IN,
                true);
    }

    private static BettingResult raise(
            GameState gameState,
            PokerPlayer player,
            long target,
            LegalActions legalActions) {
        if (target < legalActions.minimumTarget()) {
            throw new BettingRuleViolationException("RAISE target is below the minimum full raise");
        }
        if (target > legalActions.maximumTarget()) {
            throw new BettingRuleViolationException("RAISE target exceeds available chips");
        }
        long previousCurrentBet = gameState.currentBet();
        long raiseSize = Math.subtractExact(target, previousCurrentBet);
        long amount = Math.subtractExact(target, player.currentBet());
        player.commitChips(amount);
        gameState.recordFullRaise(target, raiseSize);
        return new BettingResult(
                PokerActionType.RAISE,
                player.userId(),
                amount,
                target,
                player.playerState() == PokerPlayerState.ALL_IN,
                true);
    }

    private static BettingResult allIn(GameState gameState, PokerPlayer player) {
        long amount = player.tableChips();
        long target = Math.addExact(player.currentBet(), amount);
        long previousCurrentBet = gameState.currentBet();
        boolean fullRaise = false;

        player.commitChips(amount);
        if (target > previousCurrentBet) {
            long raiseSize = Math.subtractExact(target, previousCurrentBet);
            if (raiseSize >= gameState.minimumRaise()) {
                if (previousCurrentBet == 0) {
                    gameState.recordOpeningBet(target);
                } else {
                    gameState.recordFullRaise(target, raiseSize);
                }
                fullRaise = true;
            } else {
                gameState.recordShortAllInRaise(target);
            }
        }
        return new BettingResult(
                PokerActionType.ALL_IN,
                player.userId(),
                amount,
                player.currentBet(),
                true,
                fullRaise);
    }

    private static BettingResult unchangedResult(PokerActionType type, PokerPlayer player) {
        return new BettingResult(
                type,
                player.userId(),
                0,
                player.currentBet(),
                player.playerState() == PokerPlayerState.ALL_IN,
                false);
    }

    private static void validateTurn(GameState gameState, BettingAction action) {
        if (gameState.currentTurnUserId() == null) {
            throw new BettingRuleViolationException("game has no current turn");
        }
        if (gameState.currentTurnUserId() != action.userId()) {
            throw new BettingRuleViolationException("action user is not the current player");
        }
        if (action.turnId() == null || !action.turnId().equals(gameState.turnId())) {
            throw new BettingRuleViolationException("turnId is missing or stale");
        }
    }

    private static long minimumTarget(GameState gameState) {
        try {
            return gameState.currentBet() == 0
                    ? gameState.minimumRaise()
                    : Math.addExact(gameState.currentBet(), gameState.minimumRaise());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long maximumCommittable(PokerPlayer player) {
        long currentBetCapacity = Long.MAX_VALUE - player.currentBet();
        long totalCommittedCapacity = Long.MAX_VALUE - player.totalCommitted();
        return Math.min(player.tableChips(), Math.min(currentBetCapacity, totalCommittedCapacity));
    }
}
