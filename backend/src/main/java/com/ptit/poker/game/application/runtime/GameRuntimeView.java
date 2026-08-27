package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import com.ptit.poker.game.domain.card.Card;
import java.util.List;
import java.util.UUID;

public record GameRuntimeView(UUID gameId, long gameSessionId, long roomId, long handId, long handNumber,
                              int dealerSeat, int smallBlindSeat, int bigBlindSeat, GamePhase phase,
                              Long currentTurnUserId, UUID turnId, long stateVersion, long currentBet,
                              long minimumRaise, long smallBlind, long bigBlind, List<Card> communityCards,
                              List<PlayerView> players,
                              boolean handCompleted, boolean sessionFinished, boolean failed) {
    public GameRuntimeView { communityCards = List.copyOf(communityCards); players = List.copyOf(players); }
    public record PlayerView(long userId, int seat, long tableChips, long currentBet, long totalCommitted,
                             PokerPlayerState participation, int holeCardCount,
                             boolean connected, boolean leaving) {}
}
