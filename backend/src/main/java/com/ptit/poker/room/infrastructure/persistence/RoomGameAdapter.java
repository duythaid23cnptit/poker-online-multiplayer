package com.ptit.poker.room.infrastructure.persistence;

import com.ptit.poker.game.application.runtime.RoomGamePort;
import com.ptit.poker.room.domain.RoomPlayerState;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.ptit.poker.room.application.RoomChangedEvent;
import com.ptit.poker.room.api.event.RoomEventType;
import java.util.Map;
import com.ptit.poker.player.application.RoomPlayerAccountPort;
import java.time.Clock;

@Component @Profile("!bootstrap")
public class RoomGameAdapter implements RoomGamePort {
    private final RoomRepository rooms; private final RoomPlayerRepository players; private final ApplicationEventPublisher events;
    private final RoomPlayerAccountPort accounts; private final Clock clock;
    public RoomGameAdapter(RoomRepository rooms, RoomPlayerRepository players, ApplicationEventPublisher events,
                           RoomPlayerAccountPort accounts, Clock clock) {
        this.rooms = rooms; this.players = players; this.events = events; this.accounts=accounts; this.clock=clock;
    }
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
    @Override @Transactional
    public void markDisconnected(long roomId, long userId) {
        require(roomId, userId).markDisconnected();
        events.publishEvent(new RoomChangedEvent(roomId, RoomEventType.PLAYER_DISCONNECTED,
                Map.of("userId", userId, "state", RoomPlayerState.DISCONNECTED), null, null));
    }
    @Override @Transactional
    public void markReconnected(long roomId, long userId) {
        require(roomId, userId).markReconnected();
        events.publishEvent(new RoomChangedEvent(roomId, RoomEventType.PLAYER_RECONNECTED,
                Map.of("userId", userId, "state", RoomPlayerState.PLAYING), null, null));
    }
    @Override @Transactional
    public void finalizeActiveGameDeparture(long roomId,long userId) {
        RoomPlayerEntity member=require(roomId,userId);
        if (!member.isActive()) return;
        long cashOut=member.leave(clock.instant());
        accounts.credit(userId,cashOut);
        events.publishEvent(new RoomChangedEvent(roomId,RoomEventType.PLAYER_LEFT,
                Map.of("userId",userId),RoomEventType.PLAYER_COUNT_CHANGED,Map.of("roomId",roomId)));
    }
    @Override @Transactional
    public void markAdministrativeLeaving(long roomId, long userId) { require(roomId, userId).markLeaving(); }
    @Override @Transactional
    public void finishRoom(long roomId) {
        rooms.findByIdForUpdate(roomId).orElseThrow(() -> new IllegalArgumentException("room not found"))
                .finish(clock.instant());
    }
    private RoomPlayerEntity require(long roomId, long userId) {
        return players.findByRoomIdAndUserIdForUpdate(roomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("room player not found"));
    }
}
