package com.ptit.poker.game.domain.settlement;

import com.ptit.poker.game.domain.hand.HandValue;
import com.ptit.poker.game.domain.pot.PotType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable award plan for one independently resolved contested pot. */
public record PotAward(
        PotType potType,
        int potIndex,
        long potAmount,
        List<Long> winnerUserIds,
        Optional<HandValue> winningHandValue,
        long baseShare,
        List<Long> oddChipUserIds,
        Map<Long, Long> winnerPayouts) {

    public PotAward {
        Objects.requireNonNull(potType, "potType must not be null");
        if (potIndex < 0 || potAmount <= 0 || baseShare < 0) {
            throw new IllegalArgumentException("pot award amounts and index are invalid");
        }
        winnerUserIds = List.copyOf(Objects.requireNonNull(winnerUserIds, "winnerUserIds must not be null"));
        if (winnerUserIds.isEmpty()) {
            throw new IllegalArgumentException("winnerUserIds must not be empty");
        }
        winningHandValue = Objects.requireNonNull(winningHandValue, "winningHandValue must not be null");
        oddChipUserIds = List.copyOf(Objects.requireNonNull(oddChipUserIds, "oddChipUserIds must not be null"));
        winnerPayouts = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(
                winnerPayouts, "winnerPayouts must not be null")));
        if (!winnerPayouts.keySet().equals(new java.util.LinkedHashSet<>(winnerUserIds))) {
            throw new IllegalArgumentException("winner payouts must match winnerUserIds");
        }
    }
}
