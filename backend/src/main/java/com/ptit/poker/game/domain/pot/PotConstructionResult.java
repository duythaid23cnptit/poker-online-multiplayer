package com.ptit.poker.game.domain.pot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, unapplied result of slicing all hand commitments. */
public record PotConstructionResult(
        Optional<Pot> mainPot,
        List<Pot> sidePots,
        List<UncalledBetReturn> uncalledBetReturns) {

    public PotConstructionResult {
        mainPot = Objects.requireNonNull(mainPot, "mainPot must not be null");
        sidePots = List.copyOf(Objects.requireNonNull(sidePots, "sidePots must not be null"));
        uncalledBetReturns = List.copyOf(Objects.requireNonNull(
                uncalledBetReturns, "uncalledBetReturns must not be null"));
    }

    public List<Pot> contestedPots() {
        if (mainPot.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<Pot> pots = new java.util.ArrayList<>(1 + sidePots.size());
        pots.add(mainPot.orElseThrow());
        pots.addAll(sidePots);
        return List.copyOf(pots);
    }

    public long contestedAmount() {
        long amount = 0;
        for (Pot pot : contestedPots()) {
            amount = Math.addExact(amount, pot.amount());
        }
        return amount;
    }

    public long uncalledAmount() {
        long amount = 0;
        for (UncalledBetReturn uncalled : uncalledBetReturns) {
            amount = Math.addExact(amount, uncalled.amount());
        }
        return amount;
    }
}
