package com.ptit.poker.game.application.runtime;

import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class ActiveGameRegistry {
    private final ConcurrentHashMap<UUID, ActiveGameContext> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, UUID> byRoom = new ConcurrentHashMap<>();
    boolean register(ActiveGameContext context) {
        if (byRoom.putIfAbsent(context.roomId, context.gameId) != null) return false;
        byId.put(context.gameId, context); return true;
    }
    ActiveGameContext require(UUID id) {
        ActiveGameContext value = byId.get(id);
        if (value == null) throw new GameRuntimeException("GAME_NOT_ACTIVE");
        return value;
    }
    void remove(ActiveGameContext context) { byId.remove(context.gameId, context); byRoom.remove(context.roomId, context.gameId); }
    Optional<ActiveGameContext> findByUser(long userId) {
        return byId.values().stream().filter(context -> {
            context.lock.lock();
            try { return context.sessionMemberUserIds.contains(userId); }
            finally { context.lock.unlock(); }
        }).findFirst();
    }
}
