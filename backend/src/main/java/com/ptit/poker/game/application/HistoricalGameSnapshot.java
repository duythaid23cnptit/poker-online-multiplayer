package com.ptit.poker.game.application;

import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import java.util.List;
import java.util.UUID;

public record HistoricalGameSnapshot(long roomId, UUID gameId, long gameSessionId, long handId,
                                     long handNumber, int dealerSeat, int smallBlindSeat, int bigBlindSeat,
                                     GamePhase phase, List<Card> board, List<Player> players,
                                     List<Card> ownHoleCards) {
    public HistoricalGameSnapshot {
        board = List.copyOf(board);
        players = List.copyOf(players);
        ownHoleCards = List.copyOf(ownHoleCards);
    }

    public record Player(long userId, int seat, long tableChips, PokerPlayerState participation,
                         boolean connected, boolean leaving) {}
}
