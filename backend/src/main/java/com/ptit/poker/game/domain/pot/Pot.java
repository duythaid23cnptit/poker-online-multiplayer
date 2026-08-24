package com.ptit.poker.game.domain.pot;

import java.util.List;
import java.util.Objects;

/** One immutable contested contribution layer. */
public record Pot(
        PotType type,
        int index,
        long amount,
        long contributionCap,
        List<Long> contributorUserIds,
        List<Long> eligibleWinnerUserIds) {

    public Pot {
        Objects.requireNonNull(type, "type must not be null");
        if (index < 0 || (type == PotType.MAIN && index != 0) || (type == PotType.SIDE && index == 0)) {
            throw new IllegalArgumentException("pot type and index are inconsistent");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (contributionCap <= 0) {
            throw new IllegalArgumentException("contributionCap must be positive");
        }
        contributorUserIds = List.copyOf(Objects.requireNonNull(
                contributorUserIds, "contributorUserIds must not be null"));
        eligibleWinnerUserIds = List.copyOf(Objects.requireNonNull(
                eligibleWinnerUserIds, "eligibleWinnerUserIds must not be null"));
        if (contributorUserIds.size() < 2) {
            throw new IllegalArgumentException("a contested pot requires at least two contributors");
        }
        if (!contributorUserIds.containsAll(eligibleWinnerUserIds)) {
            throw new IllegalArgumentException("eligible winners must be contributors");
        }
    }
}
