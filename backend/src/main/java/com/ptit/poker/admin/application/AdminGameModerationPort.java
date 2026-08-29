package com.ptit.poker.admin.application;

public interface AdminGameModerationPort {
    Change terminate(long gameSessionId);
    record Change(boolean changed, boolean deferred) {}
}
