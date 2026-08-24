package com.ptit.poker.game.domain.settlement;

import com.ptit.poker.game.domain.hand.HandValue;
import com.ptit.poker.game.domain.pot.UncalledBetReturn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable audit snapshot of a completed hand settlement. */
public record HandSettlementResult(
        Map<Long, HandValue> evaluatedHands,
        List<PotAward> potAwards,
        List<UncalledBetReturn> uncalledBetReturns,
        Map<Long, Long> totalCredits,
        long totalPayout,
        boolean foldOnly,
        long chipsBeforeSettlement,
        long chipsAfterSettlement) {

    public HandSettlementResult {
        evaluatedHands = immutableMap(evaluatedHands, "evaluatedHands");
        potAwards = List.copyOf(Objects.requireNonNull(potAwards, "potAwards must not be null"));
        uncalledBetReturns = List.copyOf(Objects.requireNonNull(
                uncalledBetReturns, "uncalledBetReturns must not be null"));
        totalCredits = immutableMap(totalCredits, "totalCredits");
        if (totalPayout < 0 || chipsBeforeSettlement < 0 || chipsAfterSettlement < 0) {
            throw new IllegalArgumentException("settlement totals must not be negative");
        }
    }

    private static <V> Map<Long, V> immutableMap(Map<Long, V> source, String name) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, name + " must not be null")));
    }
}
