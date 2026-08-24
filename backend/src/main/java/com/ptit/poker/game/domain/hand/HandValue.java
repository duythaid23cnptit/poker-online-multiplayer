package com.ptit.poker.game.domain.hand;

import java.util.List;
import java.util.Objects;

/** An immutable, fully comparable value for a five-card Poker hand. */
public record HandValue(HandCategory category, List<Integer> tieBreakValues)
        implements Comparable<HandValue> {

    public HandValue {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(tieBreakValues, "tieBreakValues must not be null");
        if (tieBreakValues.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("tieBreakValues must not contain null");
        }
        tieBreakValues = List.copyOf(tieBreakValues);
    }

    @Override
    public int compareTo(HandValue other) {
        Objects.requireNonNull(other, "other must not be null");
        int categoryComparison = Integer.compare(category.strength(), other.category.strength());
        if (categoryComparison != 0) {
            return categoryComparison;
        }

        int sharedSize = Math.min(tieBreakValues.size(), other.tieBreakValues.size());
        for (int index = 0; index < sharedSize; index++) {
            int valueComparison = Integer.compare(
                    tieBreakValues.get(index), other.tieBreakValues.get(index));
            if (valueComparison != 0) {
                return valueComparison;
            }
        }
        return Integer.compare(tieBreakValues.size(), other.tieBreakValues.size());
    }
}
