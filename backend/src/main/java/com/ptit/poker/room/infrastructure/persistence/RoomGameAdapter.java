package com.ptit.poker.room.infrastructure.persistence;

import com.ptit.poker.game.application.runtime.RoomGamePort;
import com.ptit.poker.room.domain.RoomPlayerState;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("!bootstrap")
public class RoomGameAdapter implements RoomGamePort {
    private final RoomRepository rooms; private final RoomPlayerRepository players;
    public RoomGameAdapter(RoomRepository rooms, RoomPlayerRepository players) { this.rooms = rooms; this.players = players; }
    @Override @Transactional(readOnly = true)
    public RoomGameSnapshot load(long roomId) {
        RoomEntity room = rooms.findById(roomId).orElseThrow(() -> new IllegalArgumentException("room not found"));
        List<RoomSeat> seats = players.findAllByRoomIdAndLeftAtIsNullOrderById(roomId).stream()
                .filter(p -> p.getSeatNumber() != null)
                .sorted(Comparator.comparingInt(RoomPlayerEntity::getSeatNumber))
                .map(p -> new RoomSeat(p.getUserId(), p.getSeatNumber(), p.getTableChips(), p.getPlayerState()))
                .toList();
        return new RoomGameSnapshot(roomId, room.getSmallBlind(), room.getBigBlind(), seats);
    }
    @Override @Transactional
    public void markPlaying(long roomId, List<Long> userIds) {
        for (long userId : userIds) require(roomId, userId).markPlaying();
    }
    @Override @Transactional
    public void synchronizeTableChips(long roomId, List<PlayerStack> stacks) {
        for (PlayerStack stack : stacks) require(roomId, stack.userId()).synchronizeTableChips(stack.tableChips());
    }
    @Override @Transactional(readOnly = true)
    public boolean canObserve(long roomId, long userId) {
        return players.findByRoomIdAndUserId(roomId, userId).filter(RoomPlayerEntity::isActive).isPresent();
    }
    private RoomPlayerEntity require(long roomId, long userId) {
        return players.findByRoomIdAndUserIdForUpdate(roomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("room player not found"));
    }
}
