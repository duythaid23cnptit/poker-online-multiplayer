package com.ptit.poker.game.api.realtime;

import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GameEventPayloads {
    private GameEventPayloads() {}
    public record Started(long gameSessionId, long handId, long handNumber) {}
    public record HandStarted(long handId, long handNumber, int dealerSeat, int smallBlindSeat, int bigBlindSeat,
                              long smallBlindAmount, long bigBlindAmount, List<PublicPlayer> players) {}
    public record HoleCards(long handId, List<Card> cards) { public HoleCards { cards = List.copyOf(cards); } }
    public record Turn(long handId, UUID turnId, Set<PokerActionType> legalActions, long callAmount,
                       long minimumTarget, long maximumTarget, long tableChips) { public Turn { legalActions = Set.copyOf(legalActions); } }
    public record PlayerAction(long userId, int seat, PokerActionType actionType, long amount,
                               long resultingCurrentBet, long resultingTableChips, UUID clientActionId) {}
    public record CommunityCards(GamePhase phase, List<Card> cards) { public CommunityCards { cards = List.copyOf(cards); } }
    public record State(long handId, long handNumber, GamePhase phase, int dealerSeat, int smallBlindSeat,
                        int bigBlindSeat, Long currentTurnUserId, long currentBet, long minimumRaise,
                        List<Card> communityCards, List<PublicPlayer> players, boolean handCompleted,
                        boolean sessionFinished) {
        public State { communityCards = List.copyOf(communityCards); players = List.copyOf(players); }
    }
    public record PublicPlayer(long userId, int seat, long tableChips, long currentBet,
                               PokerPlayerState participation, boolean connected, boolean leaving) {}
    /** Phase 7A safe policy: no private cards are revealed until explicit muck/reveal state exists. */
    public record Showdown(long handId, List<Card> board) { public Showdown { board = List.copyOf(board); } }
    public record Award(int potIndex, String potType, long potAmount, List<Long> winnerUserIds,
                        long baseShare, List<Long> oddChipUserIds, java.util.Map<Long, Long> winnerPayouts) {}
    public record Return(long userId, long amount) {}
    public record Result(long handId, List<Award> awards, List<Return> uncalledReturns,
                         List<PublicPlayer> finalPlayers, String endReason) {
        public Result { awards = List.copyOf(awards); uncalledReturns = List.copyOf(uncalledReturns); finalPlayers = List.copyOf(finalPlayers); }
    }
    public record HandFinished(long handId, long handNumber, String endReason) {}
    public record CommandError(String code, UUID clientActionId) {}
}
