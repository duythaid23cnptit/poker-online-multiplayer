package com.ptit.poker.game.domain.state;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.pot.PotBuilder;
import com.ptit.poker.game.domain.pot.PotConstructionResult;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Authoritative in-memory state of one Poker hand within a game session. */
public final class GameState {

    private static final int MAXIMUM_PLAYERS = 9;

    private final UUID gameId;
    private final UUID handId;
    private GamePhase phase;
    private final int dealerPosition;
    private final int smallBlindPosition;
    private final int bigBlindPosition;
    private Long currentTurnUserId;
    private long currentBet;
    private long minimumRaise;
    private List<Card> communityCards;
    private final List<PokerPlayer> players;
    private final Duration remainingTime;
    private UUID turnId;
    private final long minimumBetBaseline;
    private long stateVersion;
    private boolean financiallySettled;

    public GameState(
            UUID gameId,
            UUID handId,
            GamePhase phase,
            int dealerPosition,
            int smallBlindPosition,
            int bigBlindPosition,
            Long currentTurnUserId,
            long currentBet,
            long minimumRaise,
            List<Card> communityCards,
            List<PokerPlayer> players,
            Duration remainingTime,
            long stateVersion,
            UUID turnId) {
        this(
                gameId,
                handId,
                phase,
                dealerPosition,
                smallBlindPosition,
                bigBlindPosition,
                currentTurnUserId,
                currentBet,
                minimumRaise,
                communityCards,
                players,
                remainingTime,
                stateVersion,
                turnId,
                minimumRaise);
    }

    public GameState(
            UUID gameId,
            UUID handId,
            GamePhase phase,
            int dealerPosition,
            int smallBlindPosition,
            int bigBlindPosition,
            Long currentTurnUserId,
            long currentBet,
            long minimumRaise,
            List<Card> communityCards,
            List<PokerPlayer> players,
            Duration remainingTime,
            long stateVersion,
            UUID turnId,
            long minimumBetBaseline) {
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.handId = Objects.requireNonNull(handId, "handId must not be null");
        if (gameId.equals(handId)) {
            throw new IllegalArgumentException("gameId and handId must be distinct");
        }
        this.phase = Objects.requireNonNull(phase, "phase must not be null");
        validateSeat(dealerPosition, "dealerPosition");
        validateSeat(smallBlindPosition, "smallBlindPosition");
        validateSeat(bigBlindPosition, "bigBlindPosition");
        this.dealerPosition = dealerPosition;
        this.smallBlindPosition = smallBlindPosition;
        this.bigBlindPosition = bigBlindPosition;
        requireNonNegative(currentBet, "currentBet");
        requireNonNegative(minimumRaise, "minimumRaise");
        if (minimumBetBaseline < 0) {
            throw new IllegalArgumentException("minimumBetBaseline must not be negative");
        }
        if (stateVersion < 0) {
            throw new IllegalArgumentException("stateVersion must not be negative");
        }

        this.players = validateAndOrderPlayers(players);
        if (this.players.stream().anyMatch(player -> player.currentBet() > currentBet)) {
            throw new IllegalArgumentException("player currentBet must not exceed GameState currentBet");
        }
        validatePositionIsOccupied(dealerPosition, "dealerPosition", this.players);
        validatePositionIsOccupied(smallBlindPosition, "smallBlindPosition", this.players);
        validatePositionIsOccupied(bigBlindPosition, "bigBlindPosition", this.players);
        this.communityCards = validateCards(communityCards, phase, this.players);
        this.currentTurnUserId = validateCurrentTurn(currentTurnUserId, this.players);
        if ((this.currentTurnUserId == null) != (turnId == null)) {
            throw new IllegalArgumentException("currentTurnUserId and turnId must both be present or absent");
        }

        this.currentBet = currentBet;
        this.minimumRaise = minimumRaise;
        this.remainingTime = validateRemainingTime(remainingTime);
        this.stateVersion = stateVersion;
        this.turnId = turnId;
        this.minimumBetBaseline = minimumBetBaseline;
    }

    public UUID gameId() {
        return gameId;
    }

    public UUID handId() {
        return handId;
    }

    public GamePhase phase() {
        return phase;
    }

    public int dealerPosition() {
        return dealerPosition;
    }

    public int smallBlindPosition() {
        return smallBlindPosition;
    }

    public int bigBlindPosition() {
        return bigBlindPosition;
    }

    public Long currentTurnUserId() {
        return currentTurnUserId;
    }

    public long currentBet() {
        return currentBet;
    }

    public long minimumRaise() {
        return minimumRaise;
    }

    /** Derives the single authoritative pot model from current hand commitments. */
    public PotConstructionResult potConstruction() {
        return PotBuilder.calculate(players);
    }

    public boolean isFinanciallySettled() {
        return financiallySettled;
    }

    public List<Card> communityCards() {
        return communityCards;
    }

    public List<PokerPlayer> players() {
        return players;
    }

    public Duration remainingTime() {
        return remainingTime;
    }

    public long stateVersion() {
        return stateVersion;
    }

    public UUID turnId() {
        return turnId;
    }

    public long minimumBetBaseline() {
        return minimumBetBaseline;
    }

    public PokerPlayer requirePlayer(long userId) {
        return players.stream()
                .filter(player -> player.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("userId does not identify a player in the game"));
    }

    /** Records the first full wager of a street. */
    public void recordOpeningBet(long newCurrentBet) {
        if (currentBet != 0) {
            throw new IllegalStateException("an opening bet requires no existing wager");
        }
        if (newCurrentBet <= 0) {
            throw new IllegalArgumentException("newCurrentBet must be positive");
        }
        currentBet = newCurrentBet;
        minimumRaise = newCurrentBet;
    }

    /** Records a full raise and establishes its size as the next minimum raise. */
    public void recordFullRaise(long newCurrentBet, long raiseSize) {
        if (newCurrentBet <= currentBet || raiseSize <= 0
                || Math.subtractExact(newCurrentBet, currentBet) != raiseSize) {
            throw new IllegalArgumentException("full raise values are inconsistent");
        }
        currentBet = newCurrentBet;
        minimumRaise = raiseSize;
    }

    /** Records a short all-in increase without reopening or changing the minimum raise. */
    public void recordShortAllInRaise(long newCurrentBet) {
        if (newCurrentBet <= currentBet) {
            throw new IllegalArgumentException("short all-in must increase currentBet");
        }
        currentBet = newCurrentBet;
    }

    /** Assigns an authoritative actor and turn token, or clears both when no actor exists. */
    public void assignTurn(Long userId, UUID newTurnId) {
        if ((userId == null) != (newTurnId == null)) {
            throw new IllegalArgumentException("userId and turnId must both be present or absent");
        }
        if (userId != null) {
            requirePlayer(userId);
        }
        currentTurnUserId = userId;
        turnId = newTurnId;
    }

    /**
     * Advances exactly one street, installs server-dealt board cards, and resets street-local wagers.
     */
    public void advanceStreet(GamePhase nextPhase, List<Card> newlyDealtCards) {
        Objects.requireNonNull(nextPhase, "nextPhase must not be null");
        Objects.requireNonNull(newlyDealtCards, "newlyDealtCards must not be null");
        GamePhase expected = switch (phase) {
            case PRE_FLOP -> GamePhase.FLOP;
            case FLOP -> GamePhase.TURN;
            case TURN -> GamePhase.RIVER;
            case RIVER -> GamePhase.SHOWDOWN;
            case SHOWDOWN, FINISHED -> throw new IllegalStateException("current phase cannot advance");
        };
        if (nextPhase != expected) {
            throw new IllegalArgumentException("invalid street transition from " + phase + " to " + nextPhase);
        }
        int requiredNewCards = switch (nextPhase) {
            case FLOP -> 3;
            case TURN, RIVER -> 1;
            case SHOWDOWN -> 0;
            default -> throw new IllegalArgumentException("nextPhase is not a street transition target");
        };
        if (newlyDealtCards.size() != requiredNewCards) {
            throw new IllegalArgumentException(nextPhase + " requires " + requiredNewCards + " newly dealt cards");
        }

        List<Card> updatedBoard = new java.util.ArrayList<>(communityCards);
        updatedBoard.addAll(newlyDealtCards);
        communityCards = validateCards(updatedBoard, nextPhase, players);
        phase = nextPhase;
        currentBet = 0;
        minimumRaise = minimumBetBaseline;
        players.stream()
                .filter(player -> player.playerState() != PokerPlayerState.FOLDED)
                .forEach(PokerPlayer::resetCurrentBetForNewStreet);
        assignTurn(null, null);
    }

    /** Marks an uncontested hand terminal without performing any pot award. */
    public void finishByFolds() {
        phase = GamePhase.FINISHED;
        assignTurn(null, null);
    }

    /** Completes a successfully planned settlement exactly once. */
    public void completeFinancialSettlement() {
        if (financiallySettled) {
            throw new IllegalStateException("hand is already financially settled");
        }
        if (phase != GamePhase.SHOWDOWN && phase != GamePhase.FINISHED) {
            throw new IllegalStateException("hand outcome must be decided before settlement");
        }
        financiallySettled = true;
        phase = GamePhase.FINISHED;
        assignTurn(null, null);
    }

    /** Advances the authoritative state version exactly once. */
    public long advanceStateVersion() {
        if (stateVersion == Long.MAX_VALUE) {
            throw new IllegalStateException("stateVersion cannot advance beyond Long.MAX_VALUE");
        }
        return ++stateVersion;
    }

    private static List<PokerPlayer> validateAndOrderPlayers(List<PokerPlayer> players) {
        Objects.requireNonNull(players, "players must not be null");
        if (players.isEmpty() || players.size() > MAXIMUM_PLAYERS) {
            throw new IllegalArgumentException("players must contain between 1 and 9 players");
        }
        if (players.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("players must not contain null");
        }
        Set<Long> userIds = new HashSet<>();
        Set<Integer> seats = new HashSet<>();
        for (PokerPlayer player : players) {
            if (!userIds.add(player.userId())) {
                throw new IllegalArgumentException("players must not contain duplicate userId");
            }
            if (!seats.add(player.seatNumber())) {
                throw new IllegalArgumentException("players must not contain duplicate seatNumber");
            }
        }
        return players.stream()
                .sorted(Comparator.comparingInt(PokerPlayer::seatNumber))
                .toList();
    }

    private static List<Card> validateCards(
            List<Card> communityCards,
            GamePhase phase,
            List<PokerPlayer> players) {
        Objects.requireNonNull(communityCards, "communityCards must not be null");
        if (communityCards.size() > 5) {
            throw new IllegalArgumentException("communityCards must not contain more than 5 cards");
        }
        if (communityCards.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("communityCards must not contain null");
        }
        validateCommunityCardCount(phase, communityCards.size());

        Set<Card> knownCards = new HashSet<>();
        for (PokerPlayer player : players) {
            for (Card holeCard : player.holeCards()) {
                if (!knownCards.add(holeCard)) {
                    throw new IllegalArgumentException("player hole cards must be unique across the game");
                }
            }
        }
        for (Card communityCard : communityCards) {
            if (!knownCards.add(communityCard)) {
                throw new IllegalArgumentException("communityCards must not duplicate known cards");
            }
        }
        return List.copyOf(communityCards);
    }

    private static void validateCommunityCardCount(GamePhase phase, int cardCount) {
        int expectedCount = switch (phase) {
            case PRE_FLOP -> 0;
            case FLOP -> 3;
            case TURN -> 4;
            case RIVER, SHOWDOWN -> 5;
            case FINISHED -> cardCount;
        };
        if (cardCount != expectedCount) {
            throw new IllegalArgumentException(
                    phase + " requires " + expectedCount + " community cards");
        }
    }

    private static Long validateCurrentTurn(Long currentTurnUserId, List<PokerPlayer> players) {
        if (currentTurnUserId != null
                && players.stream().noneMatch(player -> player.userId() == currentTurnUserId)) {
            throw new IllegalArgumentException("currentTurnUserId must identify a player in the game");
        }
        return currentTurnUserId;
    }

    private static Duration validateRemainingTime(Duration remainingTime) {
        Objects.requireNonNull(remainingTime, "remainingTime must not be null");
        if (remainingTime.isNegative()) {
            throw new IllegalArgumentException("remainingTime must not be negative");
        }
        return remainingTime;
    }

    private static void validatePositionIsOccupied(
            int position,
            String name,
            List<PokerPlayer> players) {
        if (players.stream().noneMatch(player -> player.seatNumber() == position)) {
            throw new IllegalArgumentException(name + " must identify an occupied seat");
        }
    }

    private static void validateSeat(int seat, String name) {
        if (seat < PokerPlayer.MINIMUM_SEAT_NUMBER || seat > PokerPlayer.MAXIMUM_SEAT_NUMBER) {
            throw new IllegalArgumentException(name + " must be between 1 and 9");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
