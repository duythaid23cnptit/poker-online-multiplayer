package com.ptit.poker.game.domain.pot;

import com.ptit.poker.game.domain.state.PokerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure calculation of contested pots and unmatched excess from authoritative commitments. */
public final class PotBuilder {

    private PotBuilder() {
    }

    public static PotConstructionResult calculate(List<PokerPlayer> players) {
        List<PokerPlayer> orderedPlayers = validateAndOrder(players);
        List<Long> levels = orderedPlayers.stream()
                .map(PokerPlayer::totalCommitted)
                .filter(commitment -> commitment > 0)
                .distinct()
                .sorted()
                .toList();

        List<Pot> pots = new ArrayList<>();
        List<UncalledBetReturn> uncalledReturns = new ArrayList<>();
        long previousLevel = 0;
        for (long level : levels) {
            long layerSize = Math.subtractExact(level, previousLevel);
            List<PokerPlayer> contributors = orderedPlayers.stream()
                    .filter(player -> player.totalCommitted() >= level)
                    .toList();
            long layerAmount = Math.multiplyExact(layerSize, (long) contributors.size());
            if (contributors.size() == 1) {
                uncalledReturns.add(new UncalledBetReturn(contributors.getFirst().userId(), layerAmount));
            } else {
                int index = pots.size();
                List<Long> contributorIds = contributors.stream().map(PokerPlayer::userId).toList();
                List<Long> eligibleIds = contributors.stream()
                        .filter(PokerPlayer::isEligibleToWin)
                        .map(PokerPlayer::userId)
                        .toList();
                pots.add(new Pot(
                        index == 0 ? PotType.MAIN : PotType.SIDE,
                        index,
                        layerAmount,
                        level,
                        contributorIds,
                        eligibleIds));
            }
            previousLevel = level;
        }

        Optional<Pot> mainPot = pots.isEmpty() ? Optional.empty() : Optional.of(pots.getFirst());
        List<Pot> sidePots = pots.size() < 2 ? List.of() : pots.subList(1, pots.size());
        return new PotConstructionResult(mainPot, sidePots, uncalledReturns);
    }

    private static List<PokerPlayer> validateAndOrder(List<PokerPlayer> players) {
        Objects.requireNonNull(players, "players must not be null");
        if (players.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("players must not contain null");
        }
        Set<Long> userIds = new HashSet<>();
        Set<Integer> seatNumbers = new HashSet<>();
        for (PokerPlayer player : players) {
            if (!userIds.add(player.userId())) {
                throw new IllegalArgumentException("players must not contain duplicate userId");
            }
            if (!seatNumbers.add(player.seatNumber())) {
                throw new IllegalArgumentException("players must not contain duplicate seatNumber");
            }
        }
        return players.stream()
                .sorted(Comparator.comparingInt(PokerPlayer::seatNumber))
                .toList();
    }
}
