package com.ptit.poker.game.application.realtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RealtimeConnectionRegistry {
    private final ConcurrentHashMap<String, Long> usersBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    public boolean register(String sessionId, long userId) {
        Long existing = usersBySession.putIfAbsent(sessionId, userId);
        if (existing != null) return false;
        Set<String> sessions = sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
        sessions.add(sessionId);
        return sessions.size() == 1;
    }

    public DisconnectResult unregister(String sessionId) {
        Long userId = usersBySession.remove(sessionId);
        if (userId == null) return DisconnectResult.duplicate();
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions == null) return new DisconnectResult(userId, true, false);
        sessions.remove(sessionId);
        boolean last = sessions.isEmpty();
        if (last) sessionsByUser.remove(userId, sessions);
        return new DisconnectResult(userId, last, true);
    }

    public int activeSessionCount(long userId) {
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    public record DisconnectResult(Long userId, boolean lastSession, boolean knownSession) {
        static DisconnectResult duplicate() { return new DisconnectResult(null, false, false); }
    }
}
