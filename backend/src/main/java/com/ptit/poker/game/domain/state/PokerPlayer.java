package com.ptit.poker.game.domain.state;

import com.ptit.poker.game.domain.card.Card;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Authoritative state for one player participating in the current hand. */
public final class PokerPlayer {

    public static final int MINIMUM_SEAT_NUMBER = 1;
    public static final int MAXIMUM_SEAT_NUMBER = 9;

    private final long userId;
    private final int seatNumber;
    private long tableChips;
    private long currentBet;
    private long totalCommitted;
    private final List<Card> holeCards;
    private PokerPlayerState playerState;

    public PokerPlayer(
            long userId,
            int seatNumber,
            long tableChips,
            long currentBet,
            long totalCommitted,
            PokerPlayerState playerState,
            List<Card> holeCards) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (seatNumber < MINIMUM_SEAT_NUMBER || seatNumber > MAXIMUM_SEAT_NUMBER) {
            throw new IllegalArgumentException("seatNumber must be between 1 and 9");
        }
        requireNonNegative(tableChips, "tableChips");
        requireNonNegative(currentBet, "currentBet");
        requireNonNegative(totalCommitted, "totalCommitted");

        this.userId = userId;
        this.seatNumber = seatNumber;
        this.tableChips = tableChips;
        this.currentBet = currentBet;
        this.totalCommitted = totalCommitted;
        this.playerState = Objects.requireNonNull(playerState, "playerState must not be null");
        this.holeCards = validateHoleCards(holeCards);
    }

    public long userId() {
        return userId;
    }

    public int seatNumber() {
        return seatNumber;
    }

    public long tableChips() {
        return tableChips;
    }

    public long currentBet() {
        return currentBet;
    }

    public long totalCommitted() {
        return totalCommitted;
    }

    public PokerPlayerState playerState() {
        return playerState;
    }

    public List<Card> holeCards() {
        return holeCards;
    }

    public void markFolded() {
        playerState = PokerPlayerState.FOLDED;
    }

    public void markAllIn() {
        playerState = PokerPlayerState.ALL_IN;
    }

    public void markDisconnected() {
        playerState = PokerPlayerState.DISCONNECTED;
    }

    public void markLeaving() {
        playerState = PokerPlayerState.LEAVING;
    }

    /** Commits chips immediately while preserving all player accounting invariants. */
    public void commitChips(long amount) {
        if (playerState != PokerPlayerState.ACTIVE) {
            throw new IllegalStateException("only an ACTIVE player can commit chips");
        }
        requireNonNegative(amount, "amount");
        if (amount > tableChips) {
            throw new IllegalArgumentException("amount cannot exceed tableChips");
        }

        long updatedCurrentBet = Math.addExact(currentBet, amount);
        long updatedTotalCommitted = Math.addExact(totalCommitted, amount);
        tableChips -= amount;
        currentBet = updatedCurrentBet;
        totalCommitted = updatedTotalCommitted;
        if (tableChips == 0) {
            playerState = PokerPlayerState.ALL_IN;
        }
    }

    private static List<Card> validateHoleCards(List<Card> holeCards) {
        Objects.requireNonNull(holeCards, "holeCards must not be null");
        if (holeCards.size() != 0 && holeCards.size() != 2) {
            throw new IllegalArgumentException("holeCards must contain exactly 0 or 2 cards");
        }
        if (holeCards.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("holeCards must not contain null");
        }
        if (new HashSet<>(holeCards).size() != holeCards.size()) {
            throw new IllegalArgumentException("holeCards must not contain duplicates");
        }
        return List.copyOf(holeCards);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
