package com.ptit.poker.social.chat.application;

import java.util.Optional;

public interface ChatRoomAccessPort {
    Optional<RoomAccess> find(long roomId, long userId);

    record RoomAccess(long roomId, boolean closed, boolean activeMember, boolean activeAccount) { }
}
