package com.ptit.poker.game.domain.hand;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Evaluates five-card hands and the best five-card hand available from five to seven cards. */
public final class HandEvaluator {

    private static final int HAND_SIZE = 5;
    private static final int MAX_HOLD_EM_CARDS = 7;

    public HandValue evaluateFive(List<Card> cards) {
        List<Card> validatedCards = validate(cards, HAND_SIZE, HAND_SIZE);
        Map<Rank, Integer> rankCounts = countRanks(validatedCards);
        boolean flush = validatedCards.stream()
                .map(Card::suit)
                .distinct()
                .count() == 1;
        int straightHigh = straightHigh(rankCounts.keySet());

        if (flush && straightHigh == Rank.ACE.strength()) {
            return new HandValue(HandCategory.ROYAL_FLUSH, List.of());
        }
        if (flush && straightHigh > 0) {
            return new HandValue(HandCategory.STRAIGHT_FLUSH, List.of(straightHigh));
        }

        List<Integer> quads = ranksWithCount(rankCounts, 4);
        if (!quads.isEmpty()) {
            return new HandValue(HandCategory.FOUR_OF_A_KIND,
                    List.of(quads.get(0), ranksWithCount(rankCounts, 1).get(0)));
        }

        List<Integer> trips = ranksWithCount(rankCounts, 3);
        List<Integer> pairs = ranksWithCount(rankCounts, 2);
        if (!trips.isEmpty() && !pairs.isEmpty()) {
            return new HandValue(HandCategory.FULL_HOUSE, List.of(trips.get(0), pairs.get(0)));
        }
        if (flush) {
            return new HandValue(HandCategory.FLUSH, descendingRanks(rankCounts));
        }
        if (straightHigh > 0) {
            return new HandValue(HandCategory.STRAIGHT, List.of(straightHigh));
        }
        if (!trips.isEmpty()) {
            List<Integer> tieBreakers = new ArrayList<>(trips);
            tieBreakers.addAll(ranksWithCount(rankCounts, 1));
            return new HandValue(HandCategory.THREE_OF_A_KIND, tieBreakers);
        }
        if (pairs.size() == 2) {
            return new HandValue(HandCategory.TWO_PAIR,
                    List.of(pairs.get(0), pairs.get(1), ranksWithCount(rankCounts, 1).get(0)));
        }
        if (pairs.size() == 1) {
            List<Integer> tieBreakers = new ArrayList<>(pairs);
            tieBreakers.addAll(ranksWithCount(rankCounts, 1));
            return new HandValue(HandCategory.ONE_PAIR, tieBreakers);
        }
        return new HandValue(HandCategory.HIGH_CARD, descendingRanks(rankCounts));
    }

    public HandValue evaluateBest(List<Card> cards) {
        List<Card> validatedCards = validate(cards, HAND_SIZE, MAX_HOLD_EM_CARDS);
        HandValue best = null;
        for (int first = 0; first <= validatedCards.size() - HAND_SIZE; first++) {
            for (int second = first + 1; second <= validatedCards.size() - 4; second++) {
                for (int third = second + 1; third <= validatedCards.size() - 3; third++) {
                    for (int fourth = third + 1; fourth <= validatedCards.size() - 2; fourth++) {
                        for (int fifth = fourth + 1; fifth < validatedCards.size(); fifth++) {
                            HandValue candidate = evaluateFive(List.of(
                                    validatedCards.get(first), validatedCards.get(second),
                                    validatedCards.get(third), validatedCards.get(fourth),
                                    validatedCards.get(fifth)));
                            if (best == null || candidate.compareTo(best) > 0) {
                                best = candidate;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static List<Card> validate(List<Card> cards, int minimumSize, int maximumSize) {
        Objects.requireNonNull(cards, "cards must not be null");
        if (cards.size() < minimumSize || cards.size() > maximumSize) {
            throw new IllegalArgumentException(
                    "card count must be between " + minimumSize + " and " + maximumSize);
        }
        if (cards.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("cards must not contain null");
        }
        if (new HashSet<>(cards).size() != cards.size()) {
            throw new IllegalArgumentException("cards must not contain duplicates");
        }
        return List.copyOf(cards);
    }

    private static Map<Rank, Integer> countRanks(List<Card> cards) {
        Map<Rank, Integer> counts = new EnumMap<>(Rank.class);
        cards.forEach(card -> counts.merge(card.rank(), 1, Integer::sum));
        return counts;
    }

    private static int straightHigh(Iterable<Rank> ranks) {
        List<Integer> strengths = new ArrayList<>();
        ranks.forEach(rank -> strengths.add(rank.strength()));
        strengths.sort(Comparator.reverseOrder());
        if (strengths.size() != HAND_SIZE) {
            return 0;
        }
        if (strengths.equals(List.of(14, 5, 4, 3, 2))) {
            return 5;
        }
        for (int index = 1; index < strengths.size(); index++) {
            if (strengths.get(index - 1) - strengths.get(index) != 1) {
                return 0;
            }
        }
        return strengths.get(0);
    }

    private static List<Integer> ranksWithCount(Map<Rank, Integer> rankCounts, int count) {
        return rankCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == count)
                .map(entry -> entry.getKey().strength())
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static List<Integer> descendingRanks(Map<Rank, Integer> rankCounts) {
        return rankCounts.keySet().stream()
                .map(Rank::strength)
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
