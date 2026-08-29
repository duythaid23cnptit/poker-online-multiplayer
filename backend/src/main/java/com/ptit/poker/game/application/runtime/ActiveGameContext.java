package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.application.HandHistoryHandle;
import com.ptit.poker.game.domain.card.Deck;
import com.ptit.poker.game.domain.round.BettingRoundState;
import com.ptit.poker.game.domain.state.GameState;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

final class ActiveGameContext {
    final UUID gameId; final long sessionId; final long roomId; final ReentrantLock lock = new ReentrantLock();
    long handNumber; long smallBlind; long bigBlind; GameState state; Deck deck; BettingRoundState round; HandHistoryHandle history;
    boolean handCompleted; boolean sessionFinished; boolean failed; boolean administrativeTerminationRequested;
    final Set<Long> reconnectExpiredUsers = new HashSet<>();
    final Set<Long> administrativeDepartingUserIds = new HashSet<>();
    final Set<Long> sessionMemberUserIds = new HashSet<>();
    ActiveGameContext(UUID gameId, long sessionId, long roomId) {
        this.gameId = gameId; this.sessionId = sessionId; this.roomId = roomId;
    }
}
