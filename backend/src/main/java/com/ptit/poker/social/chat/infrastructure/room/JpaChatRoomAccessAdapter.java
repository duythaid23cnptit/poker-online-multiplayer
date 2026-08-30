package com.ptit.poker.social.chat.infrastructure.room;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.social.chat.application.ChatRoomAccessPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile("!bootstrap")
class JpaChatRoomAccessAdapter implements ChatRoomAccessPort {
    private final RoomRepository rooms;
    private final RoomPlayerRepository members;
    private final UserRepository users;

    JpaChatRoomAccessAdapter(RoomRepository rooms, RoomPlayerRepository members, UserRepository users) {
        this.rooms = rooms;
        this.members = members;
        this.users = users;
    }

    @Override
    public Optional<RoomAccess> find(long roomId, long userId) {
        return rooms.findById(roomId).map(room -> new RoomAccess(roomId, room.getStatus() == RoomStatus.CLOSED,
                members.findByRoomIdAndUserId(roomId, userId).filter(member -> member.getLeftAt() == null).isPresent(),
                users.findById(userId).map(user -> user.getAccountStatus() == AccountStatus.ACTIVE).orElse(false)));
    }
}
