package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.common.realtime.AuthenticatedConnectionTransition;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.realtime.RealtimeConnectionRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StompConnectionLifecycleListenerTests {
    private final RealtimeConnectionRegistry connections = new RealtimeConnectionRegistry();
    private final GameRealtimeApplicationService games = mock(GameRealtimeApplicationService.class);
    private final ApplicationEventPublisher transitions = mock(ApplicationEventPublisher.class);
    private final StompConnectionLifecycleListener listener =
            new StompConnectionLifecycleListener(connections, games, transitions);

    @Test void emitsOnlyTheFirstAndLastNeutralConnectionTransitions() {
        listener.connected(connected("first", 7));
        listener.connected(connected("second", 7));
        listener.disconnected(disconnected("first"));
        listener.disconnected(disconnected("second"));
        listener.disconnected(disconnected("second"));

        verify(transitions).publishEvent(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.FIRST_CONNECTION));
        verify(transitions).publishEvent(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.LAST_DISCONNECT));
        verify(transitions, times(2)).publishEvent(org.mockito.ArgumentMatchers.any(AuthenticatedConnectionTransition.class));
        assertThat(connections.activeSessionCount(7)).isZero();
    }

    @Test void eventFailureDoesNotCorruptFirstConnectionRegistryState() {
        doThrow(new IllegalStateException("game listener failure")).when(transitions)
                .publishEvent(org.mockito.ArgumentMatchers.any(AuthenticatedConnectionTransition.class));

        assertThatThrownBy(() -> listener.connected(connected("first", 7)))
                .isInstanceOf(IllegalStateException.class).hasMessage("game listener failure");

        assertThat(connections.activeSessionCount(7)).isOne();
    }

    @Test void eventFailureDoesNotCorruptLastDisconnectRegistryState() {
        connections.register("last", 7);
        doThrow(new IllegalStateException("game listener failure")).when(transitions)
                .publishEvent(org.mockito.ArgumentMatchers.any(AuthenticatedConnectionTransition.class));

        assertThatThrownBy(() -> listener.disconnected(disconnected("last")))
                .isInstanceOf(IllegalStateException.class).hasMessage("game listener failure");

        assertThat(connections.activeSessionCount(7)).isZero();
        assertThat(connections.unregister("last").knownSession()).isFalse();
    }

    private static SessionConnectedEvent connected(String sessionId, long userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "player-" + userId, Role.PLAYER);
        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECTED);
        headers.setSessionId(sessionId);
        headers.setUser(authentication);
        headers.setLeaveMutable(true);
        return new SessionConnectedEvent(StompConnectionLifecycleListenerTests.class, message(headers));
    }

    private static SessionDisconnectEvent disconnected(String sessionId) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        headers.setSessionId(sessionId);
        headers.setLeaveMutable(true);
        return new SessionDisconnectEvent(StompConnectionLifecycleListenerTests.class, message(headers),
                sessionId, CloseStatus.NORMAL);
    }

    private static Message<byte[]> message(StompHeaderAccessor headers) {
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
