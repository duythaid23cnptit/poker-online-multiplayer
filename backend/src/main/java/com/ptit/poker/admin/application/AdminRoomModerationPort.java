package com.ptit.poker.admin.application;

public interface AdminRoomModerationPort {
    Change removePlayer(long roomId, long userId);
    Change close(long roomId);
    record Change(boolean changed, boolean deferred, Long gameSessionId) {}
}
