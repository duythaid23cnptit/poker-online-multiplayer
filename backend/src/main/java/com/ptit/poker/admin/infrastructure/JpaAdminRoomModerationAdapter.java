package com.ptit.poker.admin.infrastructure;

import com.ptit.poker.admin.application.*;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.infrastructure.persistence.*;
import com.ptit.poker.player.application.RoomPlayerAccountPort;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.infrastructure.persistence.*;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!bootstrap")
public class JpaAdminRoomModerationAdapter implements AdminRoomModerationPort {
    private final RoomRepository rooms; private final RoomPlayerRepository members;
    private final GameSessionRepository sessions; private final RoomPlayerAccountPort accounts;
    private final GameRuntimeService runtime; private final Clock clock;
    public JpaAdminRoomModerationAdapter(RoomRepository rooms, RoomPlayerRepository members,
            GameSessionRepository sessions, RoomPlayerAccountPort accounts, GameRuntimeService runtime, Clock clock) {
        this.rooms=rooms; this.members=members; this.sessions=sessions; this.accounts=accounts; this.runtime=runtime; this.clock=clock;
    }
    @Override public Change removePlayer(long roomId, long userId) {
        RoomEntity room=requireRoom(roomId);
        RoomPlayerEntity member=members.findByRoomIdAndUserIdForUpdate(roomId,userId).orElseThrow(()->notFound("ROOM_PLAYER_NOT_FOUND","Room player not found"));
        if(!member.isActive())return new Change(false,false,null);
        var activeSession=sessions.findFirstByRoomIdAndStatusOrderByStartedAtDesc(roomId,GameSessionStatus.ACTIVE);
        if(activeSession.isPresent()){
            boolean changed=runtime.requestAdministrativeRemoval(roomId,userId);
            return new Change(changed,true,activeSession.orElseThrow().getId());
        }
        accounts.credit(userId,member.leave(clock.instant()));
        if(room.getOwnerUserId().equals(userId)){
            List<RoomPlayerEntity> remaining=members.findAllByRoomIdAndLeftAtIsNullOrderById(roomId);
            if(remaining.isEmpty())room.close(clock.instant()); else room.transferOwnershipTo(remaining.getFirst().getUserId(),clock.instant());
        } else room.recordActivity(clock.instant());
        return new Change(true,false,null);
    }
    @Override public Change close(long roomId) {
        RoomEntity room=requireRoom(roomId);
        if(room.getStatus()==RoomStatus.CLOSED)return new Change(false,false,null);
        var active=sessions.findFirstByRoomIdAndStatusOrderByStartedAtDesc(roomId,GameSessionStatus.ACTIVE);
        if(active.isPresent())throw conflict("ROOM_HAS_ACTIVE_GAME","Active game must be terminated before closing the room");
        for(RoomPlayerEntity member:members.findAllByRoomIdAndLeftAtIsNullOrderById(roomId))
            accounts.credit(member.getUserId(),member.leave(clock.instant()));
        room.close(clock.instant());
        return new Change(true,false,null);
    }
    private RoomEntity requireRoom(long id){return rooms.findByIdForUpdate(id).orElseThrow(()->notFound("ROOM_NOT_FOUND","Room not found"));}
    private static AdminModerationException notFound(String code,String message){return new AdminModerationException(code,message,HttpStatus.NOT_FOUND);}
    private static AdminModerationException conflict(String code,String message){return new AdminModerationException(code,message,HttpStatus.CONFLICT);}
}
