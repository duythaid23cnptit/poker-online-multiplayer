package com.ptit.poker.room.infrastructure.realtime;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.room.application.RoomApplicationService;
import com.ptit.poker.game.application.runtime.GameRuntimeService;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!bootstrap")
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {
    private final JwtService jwt;
    private final UserRepository users;
    private final RoomApplicationService rooms;
    private final GameRuntimeService games;

    public WebSocketAuthenticationInterceptor(JwtService jwt, UserRepository users, RoomApplicationService rooms,
                                              GameRuntimeService games) {
        this.jwt = jwt; this.users = users; this.rooms = rooms; this.games = games;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }
        if (accessor.getCommand() == StompCommand.CONNECT) authenticate(accessor);
        if (accessor.getCommand() == StompCommand.SUBSCRIBE || accessor.getCommand() == StompCommand.SEND)
            requireCurrentlyActive(accessor);
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) authorizeSubscription(accessor);
        if (accessor.getCommand() == StompCommand.SEND && accessor.getUser() == null)
            throw new IllegalArgumentException("Authentication required");
        return message;
    }

    private void requireCurrentlyActive(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof org.springframework.security.core.Authentication authentication)
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)
                || users.findById(principal.userId()).filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE).isEmpty())
            throw new IllegalArgumentException("Authentication required");
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new IllegalArgumentException("Authentication required");
        try {
            Long id = jwt.parseAccessTokenSubject(header.substring(7));
            UserEntity user = users.findById(id).filter(value -> value.getAccountStatus() == AccountStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalArgumentException("Authentication required"));
            AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Authentication required");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) throw new IllegalArgumentException("Authentication required");
        String destination = accessor.getDestination();
        if ("/topic/lobby".equals(destination)) return;
        if ("/user/queue/private".equals(destination)) return;
        if ("/user/queue/notifications".equals(destination)) return;
        AuthenticatedUser user = (AuthenticatedUser) ((org.springframework.security.core.Authentication) accessor.getUser()).getPrincipal();
        if (destination != null && destination.startsWith("/topic/game/")) {
            UUID gameId;
            try { gameId = UUID.fromString(destination.substring("/topic/game/".length())); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid destination"); }
            if (games.canObserve(gameId, user.userId())) return;
            throw new IllegalArgumentException("Subscription forbidden");
        }
        if (destination != null && destination.startsWith("/topic/room/")) {
            Long roomId;
            try { roomId = Long.valueOf(destination.substring("/topic/room/".length())); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid destination"); }
            if (rooms.isActiveMember(roomId, user.userId())) return;
        }
        throw new IllegalArgumentException("Subscription forbidden");
    }
}
