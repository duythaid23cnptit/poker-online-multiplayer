package com.ptit.poker.game.application.runtime;

import com.ptit.poker.room.domain.RoomPlayerState;
import java.util.List;

public interface RoomGamePort {
    RoomGameSnapshot load(long roomId);
    void markPlaying(long roomId, List<Long> userIds);
    void synchronizeTableChips(long roomId, List<PlayerStack> stacks);
    boolean canObserve(long roomId, long userId);
    void markDisconnected(long roomId, long userId);
    void markReconnected(long roomId, long userId);
    void finalizeActiveGameDeparture(long roomId, long userId);

    record RoomGameSnapshot(long roomId, long smallBlind, long bigBlind, List<RoomSeat> seats) {
        public RoomGameSnapshot { seats = List.copyOf(seats); }
    }
    record RoomSeat(long userId, int seatNumber, long tableChips, RoomPlayerState state) {}
    record PlayerStack(long userId, long tableChips) {}
}
