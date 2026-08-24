package com.ptit.poker.game.domain.settlement;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.hand.HandEvaluator;
import com.ptit.poker.game.domain.hand.HandValue;
import com.ptit.poker.game.domain.pot.Pot;
import com.ptit.poker.game.domain.pot.PotConstructionResult;
import com.ptit.poker.game.domain.pot.UncalledBetReturn;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Plans and atomically applies authoritative table-chip settlement for one hand. */
public final class HandSettlementEngine {

    private final HandEvaluator handEvaluator;

    public HandSettlementEngine(HandEvaluator handEvaluator) {
        this.handEvaluator = Objects.requireNonNull(handEvaluator, "handEvaluator must not be null");
    }

    public HandSettlementResult settleShowdown(GameState gameState) {
        Objects.requireNonNull(gameState, "gameState must not be null");
        requireUnsettled(gameState);
        if (gameState.phase() != GamePhase.SHOWDOWN) {
            throw new IllegalStateException("normal showdown settlement requires SHOWDOWN phase");
        }
        if (gameState.communityCards().size() != 5) {
            throw new IllegalStateException("normal showdown requires exactly 5 community cards");
        }

        PotConstructionResult pots = gameState.potConstruction();
        if (pots.contestedPots().isEmpty()) {
            throw new IllegalStateException("showdown requires at least one contested pot");
        }
        Set<Long> candidates = evaluationCandidates(pots);
        Map<Long, HandValue> evaluatedHands = evaluateCandidates(gameState, candidates);
        List<PotAward> awards = pots.contestedPots().stream()
                .map(pot -> pot.eligibleWinnerUserIds().size() == 1
                        ? resolveSingleEligiblePot(pot)
                        : resolveShowdownPot(gameState, pot, evaluatedHands))
                .toList();
        return applyPlan(gameState, pots, evaluatedHands, awards, false);
    }

    public HandSettlementResult settleByFolds(GameState gameState) {
        Objects.requireNonNull(gameState, "gameState must not be null");
        requireUnsettled(gameState);
        if (gameState.phase() != GamePhase.FINISHED) {
            throw new IllegalStateException("fold-only settlement requires a decided FINISHED hand");
        }

        PotConstructionResult pots = gameState.potConstruction();
        List<Long> remainingPlayers = gameState.players().stream()
                .filter(PokerPlayer::isEligibleToWin)
                .map(PokerPlayer::userId)
                .toList();
        if (remainingPlayers.size() != 1) {
            throw new IllegalStateException("fold-only settlement requires one remaining contender");
        }
        long remainingPlayer = remainingPlayers.getFirst();
        for (Pot pot : pots.contestedPots()) {
            if (!pot.eligibleWinnerUserIds().equals(List.of(remainingPlayer))) {
                throw new IllegalStateException("fold-only pots require exactly one eligible winner");
            }
        }
        List<PotAward> awards = pots.contestedPots().stream()
                .map(this::resolveSingleEligiblePot)
                .toList();
        return applyPlan(gameState, pots, Map.of(), awards, true);
    }

    private Map<Long, HandValue> evaluateCandidates(GameState gameState, Set<Long> candidates) {
        Map<Long, HandValue> values = new LinkedHashMap<>();
        gameState.players().stream()
                .filter(player -> candidates.contains(player.userId()))
                .forEach(player -> {
                    if (player.holeCards().size() != 2) {
                        throw new IllegalStateException("eligible showdown player must have exactly 2 hole cards");
                    }
                    List<Card> sevenCards = new ArrayList<>(gameState.communityCards());
                    sevenCards.addAll(player.holeCards());
                    values.put(player.userId(), handEvaluator.evaluateBest(sevenCards));
                });
        if (!values.keySet().equals(candidates)) {
            throw new IllegalStateException("pot eligibility references a player outside the game");
        }
        return values;
    }

    private PotAward resolveShowdownPot(
            GameState gameState,
            Pot pot,
            Map<Long, HandValue> evaluatedHands) {
        if (pot.eligibleWinnerUserIds().isEmpty()) {
            throw new IllegalStateException("contested pot has no eligible winner");
        }
        HandValue best = pot.eligibleWinnerUserIds().stream()
                .map(evaluatedHands::get)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("eligible pot player was not evaluated"));
        List<Long> winners = pot.eligibleWinnerUserIds().stream()
                .filter(userId -> best.equals(evaluatedHands.get(userId)))
                .toList();
        return calculateAward(gameState, pot, winners, Optional.of(best));
    }

    private PotAward resolveSingleEligiblePot(Pot pot) {
        if (pot.eligibleWinnerUserIds().size() != 1) {
            throw new IllegalStateException("pot is not directly awardable");
        }
        long winner = pot.eligibleWinnerUserIds().getFirst();
        return new PotAward(
                pot.type(), pot.index(), pot.amount(), List.of(winner), Optional.empty(),
                pot.amount(), List.of(), Map.of(winner, pot.amount()));
    }

    PotAward calculateAward(
            GameState gameState,
            Pot pot,
            List<Long> winners,
            Optional<HandValue> winningValue) {
        if (winners.isEmpty()) {
            throw new IllegalStateException("pot has no winner");
        }
        long baseShare = pot.amount() / winners.size();
        int remainder = Math.toIntExact(pot.amount() % winners.size());
        List<Long> clockwiseWinners = clockwiseFromDealer(gameState, winners);
        List<Long> oddChipUsers = List.copyOf(clockwiseWinners.subList(0, remainder));
        Map<Long, Long> payouts = new LinkedHashMap<>();
        for (long winner : clockwiseWinners) {
            long payout = oddChipUsers.contains(winner) ? Math.addExact(baseShare, 1) : baseShare;
            payouts.put(winner, payout);
        }
        return new PotAward(
                pot.type(), pot.index(), pot.amount(), clockwiseWinners, winningValue,
                baseShare, oddChipUsers, payouts);
    }

    private List<Long> clockwiseFromDealer(GameState gameState, List<Long> winners) {
        Set<Long> winnerSet = Set.copyOf(winners);
        return gameState.players().stream()
                .filter(player -> winnerSet.contains(player.userId()))
                .sorted(Comparator
                        .comparingInt((PokerPlayer player) -> clockwiseDistance(
                                gameState.dealerPosition(), player.seatNumber()))
                        .thenComparingInt(PokerPlayer::seatNumber))
                .map(PokerPlayer::userId)
                .toList();
    }

    private static int clockwiseDistance(int dealerSeat, int playerSeat) {
        int distance = playerSeat - dealerSeat;
        return distance > 0 ? distance : distance + PokerPlayer.MAXIMUM_SEAT_NUMBER;
    }

    private HandSettlementResult applyPlan(
            GameState gameState,
            PotConstructionResult pots,
            Map<Long, HandValue> evaluatedHands,
            List<PotAward> awards,
            boolean foldOnly) {
        Map<Long, Long> credits = new LinkedHashMap<>();
        for (PotAward award : awards) {
            award.winnerPayouts().forEach((userId, amount) -> mergeExact(credits, userId, amount));
        }
        for (UncalledBetReturn uncalled : pots.uncalledBetReturns()) {
            gameState.requirePlayer(uncalled.userId());
            mergeExact(credits, uncalled.userId(), uncalled.amount());
        }

        long committed = sumCommitted(gameState.players());
        long planned = Math.addExact(pots.contestedAmount(), pots.uncalledAmount());
        if (committed != planned || sumValues(credits) != planned) {
            throw new IllegalStateException("settlement plan does not conserve committed chips");
        }
        long tableBefore = sumTableChips(gameState.players());
        long systemBefore = Math.addExact(tableBefore, committed);
        long projectedAfter = 0;
        for (PokerPlayer player : gameState.players()) {
            projectedAfter = Math.addExact(projectedAfter,
                    Math.addExact(player.tableChips(), credits.getOrDefault(player.userId(), 0L)));
        }
        if (systemBefore != projectedAfter) {
            throw new IllegalStateException("settlement changes the table chip total");
        }

        for (PokerPlayer player : gameState.players()) {
            player.applyHandSettlement(credits.getOrDefault(player.userId(), 0L));
        }
        gameState.completeFinancialSettlement();
        return new HandSettlementResult(
                evaluatedHands, awards, pots.uncalledBetReturns(), credits, planned,
                foldOnly, systemBefore, projectedAfter);
    }

    private static Set<Long> evaluationCandidates(PotConstructionResult pots) {
        Set<Long> candidates = new LinkedHashSet<>();
        for (Pot pot : pots.contestedPots()) {
            if (pot.eligibleWinnerUserIds().isEmpty()) {
                throw new IllegalStateException("contested pot has no eligible winner");
            }
            if (pot.eligibleWinnerUserIds().size() > 1) {
                candidates.addAll(pot.eligibleWinnerUserIds());
            }
        }
        return candidates;
    }

    private static void requireUnsettled(GameState gameState) {
        if (gameState.isFinanciallySettled()) {
            throw new IllegalStateException("hand is already financially settled");
        }
    }

    private static void mergeExact(Map<Long, Long> values, long userId, long amount) {
        values.merge(userId, amount, Math::addExact);
    }

    private static long sumCommitted(List<PokerPlayer> players) {
        long total = 0;
        for (PokerPlayer player : players) {
            total = Math.addExact(total, player.totalCommitted());
        }
        return total;
    }

    private static long sumTableChips(List<PokerPlayer> players) {
        long total = 0;
        for (PokerPlayer player : players) {
            total = Math.addExact(total, player.tableChips());
        }
        return total;
    }

    private static long sumValues(Map<Long, Long> values) {
        long total = 0;
        for (long value : values.values()) {
            total = Math.addExact(total, value);
        }
        return total;
    }
}
