package com.ptit.poker.game.application.runtime;

import com.ptit.poker.room.domain.RoomPlayerState;
import java.util.List;

public interface RoomGamePort {
    RoomGameSnapshot load(long roomId);
    void markPlaying(long roomId, List<Long> userIds);
    void synchronizeTableChips(long roomId, List<PlayerStack> stacks);

    record RoomGameSnapshot(long roomId, long smallBlind, long bigBlind, List<RoomSeat> seats) {
        public RoomGameSnapshot { seats = List.copyOf(seats); }
    }
    record RoomSeat(long userId, int seatNumber, long tableChips, RoomPlayerState state) {}
    record PlayerStack(long userId, long tableChips) {}
}
