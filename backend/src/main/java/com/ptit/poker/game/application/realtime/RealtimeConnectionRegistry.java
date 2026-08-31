package com.ptit.poker.game.application.realtime;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongConsumer;
import org.springframework.stereotype.Component;

@Component
public class RealtimeConnectionRegistry {
    private final ConcurrentHashMap<String, SessionBinding> usersBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, UserSessions> sessionsByUser = new ConcurrentHashMap<>();

    public boolean register(String sessionId, long userId) {
        if (sessionId == null) return false;
        return register(sessionId, userId, () -> { });
    }

    public boolean register(String sessionId, long userId, Runnable onFirstConnection) {
        if (sessionId == null) return false;
        Objects.requireNonNull(onFirstConnection);
        while (true) {
            UserSessions sessions = sessionsByUser.computeIfAbsent(userId, ignored -> new UserSessions());
            sessions.lock.lock();
            try {
                if (sessionsByUser.get(userId) != sessions) continue;
                SessionBinding binding = new SessionBinding(userId, sessions);
                if (usersBySession.putIfAbsent(sessionId, binding) != null) return false;
                boolean first = sessions.sessionIds.isEmpty();
                sessions.sessionIds.add(sessionId);
                if (first) onFirstConnection.run();
                return first;
            } finally {
                sessions.lock.unlock();
            }
        }
    }

    public DisconnectResult unregister(String sessionId) {
        if (sessionId == null) return DisconnectResult.duplicate();
        return unregister(sessionId, ignored -> { });
    }

    public DisconnectResult unregister(String sessionId, LongConsumer onLastDisconnect) {
        if (sessionId == null) return DisconnectResult.duplicate();
        Objects.requireNonNull(onLastDisconnect);
        SessionBinding binding = usersBySession.get(sessionId);
        if (binding == null) return DisconnectResult.duplicate();
        binding.sessions().lock.lock();
        try {
            if (usersBySession.get(sessionId) != binding) return DisconnectResult.duplicate();
            binding.sessions().sessionIds.remove(sessionId);
            usersBySession.remove(sessionId, binding);
            boolean last = binding.sessions().sessionIds.isEmpty();
            if (!last) return new DisconnectResult(binding.userId(), false, true);
            try {
                onLastDisconnect.accept(binding.userId());
            } finally {
                sessionsByUser.remove(binding.userId(), binding.sessions());
            }
            return new DisconnectResult(binding.userId(), true, true);
        } finally {
            binding.sessions().lock.unlock();
        }
    }

    public int activeSessionCount(long userId) {
        while (true) {
            UserSessions sessions = sessionsByUser.get(userId);
            if (sessions == null) return 0;
            sessions.lock.lock();
            try {
                if (sessionsByUser.get(userId) == sessions) return sessions.sessionIds.size();
            } finally {
                sessions.lock.unlock();
            }
        }
    }

    public record DisconnectResult(Long userId, boolean lastSession, boolean knownSession) {
        static DisconnectResult duplicate() { return new DisconnectResult(null, false, false); }
    }

    private record SessionBinding(long userId, UserSessions sessions) { }

    private static final class UserSessions {
        private final ReentrantLock lock = new ReentrantLock();
        private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();
    }
}
