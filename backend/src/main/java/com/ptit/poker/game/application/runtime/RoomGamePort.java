package com.ptit.poker.game.application.runtime;

import com.ptit.poker.room.domain.RoomPlayerState;
import com.ptit.poker.room.domain.RoomStatus;
import java.util.List;

public interface RoomGamePort {
    RoomGameSnapshot load(long roomId);
    default RoomGameSnapshot loadForStart(long roomId) { return load(roomId); }
    void markPlaying(long roomId, List<Long> userIds);
    void synchronizeTableChips(long roomId, List<PlayerStack> stacks);
    boolean canObserve(long roomId, long userId);
    void markDisconnected(long roomId, long userId);
    void markReconnected(long roomId, long userId);
    void finalizeActiveGameDeparture(long roomId, long userId);
    void markLeaving(long roomId, long userId);
    void finishRoom(long roomId);

    record RoomGameSnapshot(long roomId, long ownerUserId, RoomStatus status, long smallBlind, long bigBlind,
                            List<RoomSeat> seats) {
        public RoomGameSnapshot { seats = List.copyOf(seats); }
        public RoomGameSnapshot(long roomId, long smallBlind, long bigBlind, List<RoomSeat> seats) {
            this(roomId, 0, RoomStatus.WAITING, smallBlind, bigBlind, seats);
        }
    }
    record RoomSeat(long userId, int seatNumber, long tableChips, RoomPlayerState state) {}
    record PlayerStack(long userId, long tableChips) {}
}
