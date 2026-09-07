package com.ptit.poker.game.api;

import com.ptit.poker.game.api.realtime.GameEventPayloads.State;
import com.ptit.poker.game.api.realtime.GameEventPayloads.Timer;
import com.ptit.poker.game.api.realtime.GameEventPayloads.Turn;
import com.ptit.poker.game.domain.card.Card;
import java.util.List;
import java.util.UUID;

public record GameSnapshotResponse(long roomId, UUID gameId, long gameSessionId, long version,
                                   State publicState, List<Card> holeCards, Turn turn, Timer timer,
                                   boolean participant) {
    public GameSnapshotResponse { holeCards = List.copyOf(holeCards); }
}
