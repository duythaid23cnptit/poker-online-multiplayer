package com.ptit.poker.game.domain.round;

import com.ptit.poker.game.domain.betting.BettingAction;
import com.ptit.poker.game.domain.betting.BettingEngine;
import com.ptit.poker.game.domain.betting.BettingResult;
import com.ptit.poker.game.domain.betting.BettingRuleViolationException;
import com.ptit.poker.game.domain.betting.LegalActions;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Deck;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Coordinates betting actions with round completion, turn order, and street transitions. */
public final class PokerRoundEngine {

    private final BettingEngine bettingEngine;
    private final Supplier<UUID> turnIdSupplier;

    public PokerRoundEngine() {
        this(new BettingEngine(), UUID::randomUUID);
    }

    public PokerRoundEngine(BettingEngine bettingEngine, Supplier<UUID> turnIdSupplier) {
        this.bettingEngine = Objects.requireNonNull(bettingEngine, "bettingEngine must not be null");
        this.turnIdSupplier = Objects.requireNonNull(turnIdSupplier, "turnIdSupplier must not be null");
    }

    /** Initializes authoritative action order for the state's current street. */
    public BettingRoundState start(GameState gameState, Deck deck) {
        Objects.requireNonNull(gameState, "gameState must not be null");
        Objects.requireNonNull(deck, "deck must not be null");
        if (gameState.minimumBetBaseline() <= 0) {
            throw new IllegalArgumentException("minimumBetBaseline must be positive for betting rounds");
        }
        Set<Long> actionable = actionablePlayerIds(gameState);
        BettingRoundState roundState = new BettingRoundState(gameState.phase(), actionable);
        if (remainingContenders(gameState) <= 1) {
            gameState.finishByFolds();
        } else if (actionable.size() <= 1) {
            advanceWithoutFurtherDecisions(gameState, roundState, deck, new ArrayList<>());
        } else {
            PokerPlayer first = firstActorForStreet(gameState, actionable);
            assignNewTurn(gameState, first.userId());
        }
        gameState.advanceStateVersion();
        return roundState;
    }

    /** Returns Phase 5D legal actions constrained by this betting round's raise rights. */
    public LegalActions legalActions(GameState gameState, BettingRoundState roundState, long userId) {
        validateRoundMatchesState(gameState, roundState);
        LegalActions base = bettingEngine.legalActions(gameState, userId);
        if (base.actions().isEmpty() || !roundState.needsResponse(userId)) {
            return LegalActions.none();
        }
        if (roundState.raiseRightsOpen(userId, gameState)) {
            return base;
        }

        EnumSet<PokerActionType> constrained = EnumSet.copyOf(base.actions());
        constrained.remove(PokerActionType.RAISE);
        if (base.maximumTarget() > gameState.currentBet()) {
            constrained.remove(PokerActionType.ALL_IN);
        }
        return new LegalActions(
                constrained,
                base.callAmount(),
                base.minimumTarget(),
                base.maximumTarget());
    }

    public RoundTransitionResult act(
            GameState gameState,
            BettingRoundState roundState,
            BettingAction action,
            Deck deck) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(deck, "deck must not be null");
        validateRoundMatchesState(gameState, roundState);
        if (!roundState.needsResponse(action.userId())) {
            throw new BettingRuleViolationException("player does not currently need a betting response");
        }
        LegalActions contextualActions = legalActions(gameState, roundState, action.userId());
        if (!contextualActions.allows(action.type())) {
            throw new BettingRuleViolationException(action.type() + " is closed by betting-round state");
        }

        long previousGameBet = gameState.currentBet();
        Long previousActor = gameState.currentTurnUserId();
        int previousActorSeat = gameState.requirePlayer(action.userId()).seatNumber();
        BettingResult bettingResult = bettingEngine.apply(gameState, action);

        Set<Long> actionable = actionablePlayerIds(gameState);
        if (bettingResult.fullRaise()) {
            roundState.recordFullRaise(action.userId(), gameState.currentBet(), actionable);
        } else {
            roundState.recordOrdinaryAction(action.userId(), gameState.currentBet());
            if (gameState.currentBet() > previousGameBet) {
                roundState.requireResponsesFrom(playersOwingChips(gameState));
            }
        }
        roundState.removeIneligible(actionable);

        List<Card> cardsDealt = new ArrayList<>();
        boolean roundCompleted = false;
        boolean streetAdvanced = false;
        boolean handDecidedByFold = false;

        if (remainingContenders(gameState) <= 1) {
            gameState.finishByFolds();
            handDecidedByFold = true;
            roundCompleted = true;
        } else if (roundComplete(gameState, roundState)) {
            roundCompleted = true;
            streetAdvanced = advanceStreetOrShowdown(gameState, roundState, deck, cardsDealt);
        } else {
            PokerPlayer next = nextActorAfterSeat(gameState, previousActorSeat, roundState.pendingResponses());
            assignNewTurn(gameState, next.userId());
        }

        return new RoundTransitionResult(
                bettingResult,
                gameState.phase(),
                previousActor,
                gameState.currentTurnUserId(),
                roundCompleted,
                streetAdvanced,
                handDecidedByFold,
                cardsDealt,
                gameState.stateVersion(),
                gameState.turnId());
    }

    private boolean advanceStreetOrShowdown(
            GameState gameState,
            BettingRoundState roundState,
            Deck deck,
            List<Card> cardsDealt) {
        if (gameState.phase() == GamePhase.SHOWDOWN || gameState.phase() == GamePhase.FINISHED) {
            gameState.assignTurn(null, null);
            return false;
        }
        advanceOneStreet(gameState, deck, cardsDealt);
        Set<Long> actionable = actionablePlayerIds(gameState);
        roundState.reset(gameState.phase(), actionable);
        if (gameState.phase() == GamePhase.SHOWDOWN) {
            return true;
        }
        if (actionable.size() <= 1) {
            advanceWithoutFurtherDecisions(gameState, roundState, deck, cardsDealt);
        } else {
            PokerPlayer first = firstActorForStreet(gameState, actionable);
            assignNewTurn(gameState, first.userId());
        }
        return true;
    }

    private void advanceWithoutFurtherDecisions(
            GameState gameState,
            BettingRoundState roundState,
            Deck deck,
            List<Card> cardsDealt) {
        while (gameState.phase() != GamePhase.SHOWDOWN && gameState.phase() != GamePhase.FINISHED) {
            advanceOneStreet(gameState, deck, cardsDealt);
            roundState.reset(gameState.phase(), actionablePlayerIds(gameState));
        }
        gameState.assignTurn(null, null);
    }

    private static void advanceOneStreet(GameState gameState, Deck deck, List<Card> cardsDealt) {
        GamePhase nextPhase = switch (gameState.phase()) {
            case PRE_FLOP -> GamePhase.FLOP;
            case FLOP -> GamePhase.TURN;
            case TURN -> GamePhase.RIVER;
            case RIVER -> GamePhase.SHOWDOWN;
            case SHOWDOWN, FINISHED -> throw new IllegalStateException("terminal phase cannot advance");
        };
        int drawCount = switch (nextPhase) {
            case FLOP -> 3;
            case TURN, RIVER -> 1;
            case SHOWDOWN -> 0;
            default -> throw new IllegalStateException("unexpected next phase");
        };
        List<Card> dealt = deck.draw(drawCount);
        gameState.advanceStreet(nextPhase, dealt);
        cardsDealt.addAll(dealt);
    }

    private static boolean roundComplete(GameState gameState, BettingRoundState roundState) {
        if (!roundState.pendingResponses().isEmpty()) {
            return false;
        }
        return decisionPlayers(gameState).stream()
                .noneMatch(player -> player.currentBet() < gameState.currentBet());
    }

    private static Set<Long> playersOwingChips(GameState gameState) {
        Set<Long> owing = new LinkedHashSet<>();
        decisionPlayers(gameState).stream()
                .filter(player -> player.currentBet() < gameState.currentBet())
                .forEach(player -> owing.add(player.userId()));
        return owing;
    }

    private static Set<Long> actionablePlayerIds(GameState gameState) {
        Set<Long> ids = new LinkedHashSet<>();
        decisionPlayers(gameState).forEach(player -> ids.add(player.userId()));
        return ids;
    }

    private static List<PokerPlayer> decisionPlayers(GameState gameState) {
        return gameState.players().stream()
                .filter(PokerPlayer::canReceiveBettingTurn)
                .toList();
    }

    private static long remainingContenders(GameState gameState) {
        return gameState.players().stream()
                .filter(PokerPlayer::isEligibleToWin)
                .count();
    }

    private static PokerPlayer firstActorForStreet(GameState gameState, Set<Long> candidates) {
        int anchor = gameState.phase() == GamePhase.PRE_FLOP
                ? gameState.bigBlindPosition()
                : gameState.dealerPosition();
        return nextActorAfterSeat(gameState, anchor, candidates);
    }

    private static PokerPlayer nextActorAfterSeat(
            GameState gameState,
            int currentSeat,
            Set<Long> candidates) {
        List<PokerPlayer> orderedCandidates = gameState.players().stream()
                .filter(player -> candidates.contains(player.userId()))
                .toList();
        return orderedCandidates.stream()
                .filter(player -> player.seatNumber() > currentSeat)
                .findFirst()
                .orElseGet(() -> orderedCandidates.stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("no eligible next actor")));
    }

    private void assignNewTurn(GameState gameState, long userId) {
        UUID turnId = Objects.requireNonNull(turnIdSupplier.get(), "generated turnId must not be null");
        gameState.assignTurn(userId, turnId);
    }

    private static void validateRoundMatchesState(GameState gameState, BettingRoundState roundState) {
        Objects.requireNonNull(gameState, "gameState must not be null");
        Objects.requireNonNull(roundState, "roundState must not be null");
        if (roundState.phase() != gameState.phase()) {
            throw new IllegalArgumentException("betting round phase does not match GameState");
        }
    }
}
