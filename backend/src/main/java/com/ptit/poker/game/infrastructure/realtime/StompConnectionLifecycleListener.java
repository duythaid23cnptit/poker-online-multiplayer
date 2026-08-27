package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.realtime.RealtimeConnectionRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!bootstrap")
public class StompConnectionLifecycleListener {
    private final RealtimeConnectionRegistry connections;
    private final GameRealtimeApplicationService games;
    public StompConnectionLifecycleListener(RealtimeConnectionRegistry connections, GameRealtimeApplicationService games) {
        this.connections = connections; this.games = games;
    }
    @EventListener public void connected(SessionConnectedEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        if (!(headers.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) return;
        String sessionId = headers.getSessionId();
        if (sessionId != null && connections.register(sessionId, user.userId())) games.onFirstConnection(user.userId());
    }
    @EventListener public void disconnected(SessionDisconnectEvent event) {
        var result = connections.unregister(event.getSessionId());
        if (result.knownSession() && result.lastSession()) games.onLastDisconnect(result.userId());
    }
    @EventListener public void subscribed(SessionSubscribeEvent event) {
        StompHeaderAccessor headers=StompHeaderAccessor.wrap(event.getMessage());
        if(!"/user/queue/private".equals(headers.getDestination()))return;
        if(headers.getUser() instanceof Authentication authentication
                &&authentication.getPrincipal() instanceof AuthenticatedUser user)games.onPrivateSubscription(user.userId());
    }
}
