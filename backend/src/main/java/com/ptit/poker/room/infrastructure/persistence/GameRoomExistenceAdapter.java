package com.ptit.poker.room.infrastructure.persistence;

import com.ptit.poker.game.application.RoomExistencePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class GameRoomExistenceAdapter implements RoomExistencePort {
    private final RoomRepository rooms;
    public GameRoomExistenceAdapter(RoomRepository rooms) { this.rooms = rooms; }
    @Override public boolean exists(long roomId) { return rooms.existsById(roomId); }
}
