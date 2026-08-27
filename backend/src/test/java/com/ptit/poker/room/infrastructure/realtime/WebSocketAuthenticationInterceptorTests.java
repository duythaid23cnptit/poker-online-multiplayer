package com.ptit.poker.room.infrastructure.realtime;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.room.application.RoomApplicationService;
import com.ptit.poker.game.application.runtime.GameRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthenticationInterceptorTests {
    private final JwtService jwt = mock(JwtService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoomApplicationService rooms = mock(RoomApplicationService.class);
    private final GameRuntimeService games = mock(GameRuntimeService.class);
    private final WebSocketAuthenticationInterceptor interceptor = new WebSocketAuthenticationInterceptor(jwt, users, rooms, games);

    @Test
    void connectAuthenticatesBearerTokenAgainstCurrentActiveUser() {
        UserEntity user = mock(UserEntity.class);
        when(jwt.parseAccessTokenSubject("valid")).thenReturn(7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(7L); when(user.getUsername()).thenReturn("player");
        when(user.getRole()).thenReturn(Role.PLAYER); when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid"); accessor.setLeaveMutable(true);
        Message<byte[]> result = (Message<byte[]>) interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class));
        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNotNull();
    }

    @Test
    void connectRejectsMissingToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT); accessor.setLeaveMutable(true);
        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Authentication required");
    }

    @Test
    void roomSubscriptionRequiresActiveMembership() {
        AuthenticatedUser principal = new AuthenticatedUser(7L, "player", Role.PLAYER);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        StompHeaderAccessor allowed = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        allowed.setUser(authentication); allowed.setDestination("/topic/room/12"); allowed.setLeaveMutable(true);
        when(rooms.isActiveMember(12L, 7L)).thenReturn(true);
        assertThat(interceptor.preSend(message(allowed), mock(org.springframework.messaging.MessageChannel.class))).isNotNull();

        StompHeaderAccessor denied = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        denied.setUser(authentication); denied.setDestination("/topic/room/13"); denied.setLeaveMutable(true);
        assertThatThrownBy(() -> interceptor.preSend(message(denied), mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Subscription forbidden");
    }

    @Test
    void gameSubscriptionRequiresRuntimeObservationRightsAndPrivateQueueIsOwnUserDestination() {
        AuthenticatedUser principal = new AuthenticatedUser(7L, "player", Role.PLAYER);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        UUID gameId = UUID.randomUUID();
        when(games.canObserve(gameId, 7L)).thenReturn(true);

        StompHeaderAccessor game = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        game.setUser(authentication); game.setDestination("/topic/game/" + gameId); game.setLeaveMutable(true);
        assertThat(interceptor.preSend(message(game), mock(org.springframework.messaging.MessageChannel.class))).isNotNull();

        StompHeaderAccessor privateQueue = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        privateQueue.setUser(authentication); privateQueue.setDestination("/user/queue/private"); privateQueue.setLeaveMutable(true);
        assertThat(interceptor.preSend(message(privateQueue), mock(org.springframework.messaging.MessageChannel.class))).isNotNull();

        when(games.canObserve(gameId, 7L)).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preSend(message(game), mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Subscription forbidden");
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
