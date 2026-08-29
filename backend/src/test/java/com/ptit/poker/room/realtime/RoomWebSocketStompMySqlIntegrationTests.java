package com.ptit.poker.room.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.room.api.dto.CreateRoomRequest;
import com.ptit.poker.room.api.dto.JoinRoomRequest;
import com.ptit.poker.room.api.event.RealtimeEvent;
import com.ptit.poker.room.api.event.RoomEventType;
import com.ptit.poker.room.application.RoomApplicationService;
import com.ptit.poker.room.domain.RoomPlayerState;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbeConfiguration.class)
class RoomWebSocketStompMySqlIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired RoomApplicationService rooms;
    @Autowired RoomPlayerRepository members;
    @Autowired SubscriptionProbe subscriptions;
    @Autowired ObjectMapper objectMapper;
    private final List<StompSession> sessions = new ArrayList<>();
    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach void disconnect() {
        RuntimeException unexpectedFailure = null;
        try {
            for (StompSession session : sessions) {
                try {
                    disconnectQuietly(session);
                } catch (RuntimeException exception) {
                    if (unexpectedFailure == null) {
                        unexpectedFailure = exception;
                    } else {
                        unexpectedFailure.addSuppressed(exception);
                    }
                }
            }
        } finally {
            clients.forEach(WebSocketStompClient::stop);
            subscriptions.reset();
        }
        if (unexpectedFailure != null) {
            throw unexpectedFailure;
        }
    }

    private void disconnectQuietly(StompSession session) {
        if (session == null) {
            return;
        }
        try {
            if (session.isConnected()) {
                session.disconnect();
            }
        } catch (MessageDeliveryException exception) {
            if (!isAlreadyClosedWebSocket(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isAlreadyClosedWebSocket(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IllegalStateException) {
                boolean tomcatClosedSessionState = java.util.Arrays.stream(current.getStackTrace())
                        .anyMatch(frame -> frame.getClassName().equals("org.apache.tomcat.websocket.WsSession")
                                && frame.getMethodName().equals("checkState"));
                if (tomcatClosedSessionState) {
                    return true;
                }

                String message = current.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase(java.util.Locale.ROOT);
                    if (normalized.contains("session")
                            && normalized.contains("closed")
                            && (normalized.contains("websocket") || normalized.contains("web socket"))) {
                        return true;
                    }
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    @Test
    void unauthenticatedConnectIsRejected() throws Exception {
        ClientHandler rejected = new ClientHandler();
        StompHeaders empty = new StompHeaders();
        client().connectAsync(url(), new WebSocketHttpHeaders(), empty, rejected);
        assertThat(rejected.failure.get(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void suspendedUserCannotConnectWithTokenIssuedWhileActive() throws Exception {
        UserEntity player=user(1_000);String oldToken=jwt.createAccessToken(player);
        player.lock();users.saveAndFlush(player);
        ClientHandler rejected=new ClientHandler();connectAsync(oldToken,rejected);
        assertThat(rejected.failure.get(5,TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void alreadyConnectedSuspendedUserCannotSendOrSubscribe() throws Exception {
        UserEntity player=user(1_000);ClientHandler sendHandler=new ClientHandler(),subscribeHandler=new ClientHandler();
        StompSession sendSession=connect(player,sendHandler),subscribeSession=connect(player,subscribeHandler);
        player.lock();users.saveAndFlush(player);
        sendSession.send("/app/room/1/ready",Map.of("clientCommandId",UUID.randomUUID(),"ready",true));
        subscribeSession.subscribe("/topic/lobby",noopHandler());
        assertThat(sendHandler.failure.get(5,TimeUnit.SECONDS)).isNotNull();
        assertThat(subscribeHandler.failure.get(5,TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void authenticatedPlayerSubscribesToLobbyAndReceivesRoomCreated() throws Exception {
        UserEntity player = user(1_000);
        ClientHandler clientHandler = new ClientHandler();
        StompSession session = connect(player, clientHandler);
        CompletableFuture<RawFrame> frame = new CompletableFuture<>();
        subscribeAndAwait(session, "/topic/lobby", rawFrameHandler(frame));

        Long roomId = rooms.create(player.getId(), room()).room().id();
        RealtimeEvent received = awaitEvent(frame, clientHandler);
        assertThat(received.type()).isEqualTo(RoomEventType.ROOM_CREATED);
        assertThat(received.scope().roomId()).isEqualTo(roomId);
    }

    @Test
    void nonMemberRoomSubscriptionIsSecurelyRejected() throws Exception {
        UserEntity owner = user(1_000); UserEntity outsider = user(1_000);
        Long roomId = rooms.create(owner.getId(), room()).room().id();
        ClientHandler deniedHandler = new ClientHandler();
        StompSession outsiderSession = connect(outsider, deniedHandler);
        outsiderSession.subscribe("/topic/room/" + roomId, noopHandler());
        assertThat(deniedHandler.failure.get(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void activeMemberSubscribesAndReadyUsesJwtPrincipal() throws Exception {
        UserEntity member = user(1_000); UserEntity outsider = user(1_000);
        Long roomId = rooms.create(member.getId(), room()).room().id();
        rooms.join(member.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        ClientHandler clientHandler = new ClientHandler();
        StompSession memberSession = connect(member, clientHandler);
        CompletableFuture<RawFrame> frame = new CompletableFuture<>();
        subscribeAndAwait(memberSession, "/topic/room/" + roomId, rawFrameHandler(frame));
        memberSession.send("/app/room/" + roomId + "/ready", Map.of(
                "clientCommandId", UUID.randomUUID(), "ready", true, "userId", outsider.getId()));

        RealtimeEvent received = awaitEvent(frame, clientHandler);
        assertThat(received.type()).isEqualTo(RoomEventType.PLAYER_READY);
        assertThat(received.scope().roomId()).isEqualTo(roomId);
        assertThat(received.payload().toString()).doesNotContain("passwordHash", "room-password");
        assertThat(members.findByRoomIdAndUserId(roomId, member.getId()).orElseThrow().getPlayerState())
                .isEqualTo(RoomPlayerState.READY);
        assertThat(members.findByRoomIdAndUserId(roomId, outsider.getId())).isEmpty();
    }

    private StompSession connect(UserEntity user) throws Exception { return connect(user, new ClientHandler()); }

    private StompSession connect(UserEntity user, ClientHandler handler) throws Exception {
        return connect(jwt.createAccessToken(user),handler);
    }
    private StompSession connect(String token, ClientHandler handler) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        StompSession session = client().connectAsync(url(), new WebSocketHttpHeaders(), headers, handler)
                .get(5, TimeUnit.SECONDS);
        sessions.add(session); return session;
    }
    private void connectAsync(String token,ClientHandler handler){StompHeaders headers=new StompHeaders();headers.add(HttpHeaders.AUTHORIZATION,"Bearer "+token);client().connectAsync(url(),new WebSocketHttpHeaders(),headers,handler);}

    private void subscribeAndAwait(StompSession session, String destination, StompFrameHandler handler)
            throws Exception {
        String subscriptionId = UUID.randomUUID().toString();
        CompletableFuture<Void> handled = subscriptions.await(subscriptionId, destination);
        StompHeaders headers = new StompHeaders();
        headers.setId(subscriptionId);
        headers.setDestination(destination);
        session.subscribe(headers, handler);
        try {
            handled.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            subscriptions.cancel(subscriptionId, destination);
            throw new AssertionError("Simple Broker did not handle expected subscription; observed="
                    + subscriptions.observed(), exception);
        }
    }

    private RealtimeEvent awaitEvent(CompletableFuture<RawFrame> frame, ClientHandler clientHandler)
            throws Exception {
        CompletableFuture.anyOf(frame, clientHandler.failure).get(5, TimeUnit.SECONDS);
        if (clientHandler.failure.isDone()) {
            throw new AssertionError("STOMP client failed before receiving the expected MESSAGE",
                    clientHandler.failure.getNow(null));
        }
        RawFrame received = frame.getNow(null);
        assertThat(received).isNotNull();
        assertThat(received.headers().getContentType()).isNotNull();
        assertThat(received.headers().getContentType().isCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
                .isTrue();
        String json = new String(received.payload(), StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("passwordHash", "password_hash", "room-password");
        try {
            return objectMapper.readValue(received.payload(), RealtimeEvent.class);
        } catch (Exception exception) {
            throw new AssertionError("STOMP MESSAGE arrived but could not be converted to RealtimeEvent; contentType="
                    + received.headers().getContentType(), exception);
        }
    }

    private WebSocketStompClient client() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(List.of(
                new ByteArrayMessageConverter(), new MappingJackson2MessageConverter())));
        clients.add(client);
        return client;
    }

    private String url() { return "ws://localhost:" + port + "/ws"; }

    private UserEntity user(long chips) {
        String username = "stomp_" + UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new UserEntity(username, passwords.encode("password"), null,
                Role.PLAYER, AccountStatus.ACTIVE, chips));
    }

    private static CreateRoomRequest room() {
        return new CreateRoomRequest("Realtime Room", RoomType.PUBLIC, 6, 5, 10, 100, null);
    }

    private static StompFrameHandler rawFrameHandler(CompletableFuture<RawFrame> frame) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                frame.complete(new RawFrame(headers, (byte[]) payload));
            }
        };
    }

    private record RawFrame(StompHeaders headers, byte[] payload) { }

    private static StompFrameHandler noopHandler() {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { }
        };
    }

    private static final class ClientHandler extends StompSessionHandlerAdapter {
        private final CompletableFuture<Throwable> failure = new CompletableFuture<>();
        @Override public void handleTransportError(StompSession session, Throwable exception) { failure.complete(exception); }
        @Override public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                              byte[] payload, Throwable exception) { failure.complete(exception); }
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            failure.complete(new IllegalStateException("STOMP ERROR: " + headers.getFirst("message")));
        }
    }

    @TestConfiguration
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class SubscriptionProbeConfiguration implements WebSocketMessageBrokerConfigurer {
        @Bean SubscriptionProbe subscriptionProbe() { return new SubscriptionProbe(); }

        @Override public void configureClientInboundChannel(ChannelRegistration registration) {
            registration.interceptors(subscriptionProbe());
        }
    }

    static final class SubscriptionProbe implements ExecutorChannelInterceptor {
        private final Map<SubscriptionKey, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();
        private final List<ObservedMessage> observed = new CopyOnWriteArrayList<>();

        CompletableFuture<Void> await(String subscriptionId, String destination) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            pending.put(new SubscriptionKey(subscriptionId, destination), future);
            return future;
        }

        List<ObservedMessage> observed() { return List.copyOf(observed); }

        void cancel(String subscriptionId, String destination) {
            pending.remove(new SubscriptionKey(subscriptionId, destination));
        }

        void reset() {
            pending.values().forEach(future -> future.cancel(false));
            pending.clear();
            observed.clear();
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            record("PRE_SEND", message, null, null);
            return message;
        }

        @Override
        public void afterSendCompletion(Message<?> message, MessageChannel channel,
                                        boolean sent, Exception exception) {
            record(sent ? "SEND_COMPLETED" : "SEND_REJECTED", message, null, exception);
        }

        @Override
        public void afterMessageHandled(Message<?> message, MessageChannel channel,
                                        MessageHandler handler, Exception exception) {
            record("HANDLED", message, handler, exception);
            SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
            String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
            if (handler instanceof SimpleBrokerMessageHandler
                    && exception == null && messageType == SimpMessageType.SUBSCRIBE) {
                SubscriptionKey key = new SubscriptionKey(subscriptionId, destination);
                CompletableFuture<Void> future = pending.remove(key);
                if (future != null) future.complete(null);
            }
        }

        private void record(String stage, Message<?> message, MessageHandler handler, Exception exception) {
            SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
            String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
            String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
            Object principal = SimpMessageHeaderAccessor.getUser(message.getHeaders());
            observed.add(new ObservedMessage(stage, messageType, destination, subscriptionId, sessionId,
                    principal == null ? null : principal.getClass().getName(),
                    handler == null ? null : handler.getClass().getName(),
                    exception == null ? null : exception.getClass().getName()));
        }

        private record SubscriptionKey(String subscriptionId, String destination) { }
        private record ObservedMessage(String stage, SimpMessageType type, String destination,
                                       String subscriptionId, String sessionId, String principalClass,
                                       String handlerClass, String exceptionClass) { }
    }
}
