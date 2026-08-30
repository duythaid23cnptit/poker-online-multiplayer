package com.ptit.poker.room.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileEntity;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
import com.ptit.poker.room.domain.RoomPlayerState;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import com.ptit.poker.social.chat.api.realtime.ChatMessageCommand;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbeConfiguration.class)
class ChatMessageWebSocketStompMySqlIntegrationTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PlayerProfileRepository profiles;
    @Autowired RoomRepository rooms;
    @Autowired RoomPlayerRepository members;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbe subscriptions;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> roomIds = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach
    void cleanup() {
        RuntimeException unexpected = null;
        try {
            for (StompSession session : sessions) {
                try { disconnectQuietly(session); }
                catch (RuntimeException failure) {
                    if (unexpected == null) unexpected = failure; else unexpected.addSuppressed(failure);
                }
            }
        } finally {
            clients.forEach(WebSocketStompClient::stop); subscriptions.reset();
            for (long roomId : roomIds) jdbc.update("DELETE FROM chat_messages WHERE room_id=?", roomId);
            for (long roomId : roomIds) jdbc.update("DELETE FROM room_players WHERE room_id=?", roomId);
            for (long roomId : roomIds) jdbc.update("DELETE FROM rooms WHERE id=?", roomId);
            for (long userId : userIds) jdbc.update("DELETE FROM player_profiles WHERE user_id=?", userId);
            for (long userId : userIds) jdbc.update("DELETE FROM users WHERE id=?", userId);
        }
        if (unexpected != null) throw unexpected;
    }

    @Test
    void activeMemberSendPersistsThenBroadcastsAuthoritativeSafeEvent() throws Exception {
        Fixture fixture = fixture(true); Frames frames = subscribe(connect(fixture.sender()), fixture.room());
        send(fixture.sender(), fixture.room(), fixture.command(), "  Xin chào 👋  ");
        JsonNode event = frames.await(); JsonNode message = event.path("payload");
        assertEnvelope(event, fixture.room(), fixture.command(), fixture.sender(), "Xin chào 👋");
        assertThat(rowCount(fixture.room(), fixture.sender(), fixture.command())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE id=? AND content='Xin chào 👋'",
                Integer.class, message.path("messageId").asLong())).isOne();
        assertThat(event.toString()).doesNotContain("password", "email", "role", "accountChips", "holeCards");
    }

    @Test
    void activeSpectatorSendIsPersistedAndBroadcastOnce() throws Exception {
        Fixture fixture = fixture(false); Frames frames = subscribe(connect(fixture.sender()), fixture.room());
        send(fixture.sender(), fixture.room(), fixture.command(), "spectator");
        assertEnvelope(frames.await(), fixture.room(), fixture.command(), fixture.sender(), "spectator");
        assertThat(frames.poll()).isNull(); assertThat(rowCount(fixture.room(), fixture.sender(), fixture.command())).isOne();
    }

    @Test
    void twoClientsInSameRoomReceiveSameAuthoritativeEvent() throws Exception {
        Fixture fixture = fixture(true); UserEntity other = user("Beta"); join(fixture.room(), other, null);
        Frames first = subscribe(connect(fixture.sender()), fixture.room());
        Frames second = subscribe(connect(other), fixture.room());
        send(fixture.sender(), fixture.room(), fixture.command(), "same room");
        JsonNode one = first.await(); JsonNode two = second.await();
        assertThat(two.path("eventId").asText()).isEqualTo(one.path("eventId").asText());
        assertThat(two.path("payload")).isEqualTo(one.path("payload"));
    }

    @Test
    void roomBroadcastIsIsolated() throws Exception {
        Fixture first = fixture(true); Fixture second = fixture(true);
        Frames firstFrames = subscribe(connect(first.sender()), first.room());
        Frames secondFrames = subscribe(connect(second.sender()), second.room());
        send(first.sender(), first.room(), first.command(), "room A");
        assertThat(firstFrames.await().path("payload").path("roomId").asLong()).isEqualTo(first.room().getId());
        assertThat(secondFrames.poll()).isNull();
    }

    @Test
    void nonMemberSendCreatesNoRowOrBroadcast() throws Exception {
        Fixture fixture = fixture(true); UserEntity outsider = user("Outsider");
        Frames observer = subscribe(connect(fixture.sender()), fixture.room());
        send(outsider, fixture.room(), fixture.command(), "forbidden");
        assertThat(observer.poll()).isNull(); assertThat(rowCount(fixture.room(), outsider, fixture.command())).isZero();
    }

    @Test
    void departedMemberSendCreatesNoRowOrBroadcast() throws Exception {
        Fixture fixture = fixture(true); UserEntity observerUser = user("Observer"); join(fixture.room(), observerUser, null);
        RoomPlayerEntity member = members.findByRoomIdAndUserId(fixture.room().getId(), fixture.sender().getId()).orElseThrow();
        member.leave(Instant.parse("2026-08-30T03:00:00Z")); members.saveAndFlush(member);
        Frames observer = subscribe(connect(observerUser), fixture.room());
        send(fixture.sender(), fixture.room(), fixture.command(), "departed");
        assertThat(observer.poll()).isNull(); assertThat(rowCount(fixture.room(), fixture.sender(), fixture.command())).isZero();
    }

    @Test
    void closedRoomSendCreatesNoRowOrBroadcast() throws Exception {
        Fixture fixture = fixture(true); Frames observer = subscribe(connect(fixture.sender()), fixture.room());
        fixture.room().close(Instant.parse("2026-08-30T03:00:00Z")); rooms.saveAndFlush(fixture.room());
        send(fixture.sender(), fixture.room(), fixture.command(), "closed");
        assertThat(observer.poll()).isNull(); assertThat(rowCount(fixture.room(), fixture.sender(), fixture.command())).isZero();
    }

    @Test
    void blankContentCreatesNoRowOrBroadcast() throws Exception {
        Fixture fixture = fixture(true); Frames observer = subscribe(connect(fixture.sender()), fixture.room());
        send(fixture.sender(), fixture.room(), fixture.command(), "  \t  ");
        assertThat(observer.poll()).isNull(); assertThat(rowCount(fixture.room(), fixture.sender(), fixture.command())).isZero();
    }

    @Test
    void forgedSenderFieldCannotOverrideAuthenticatedSender() throws Exception {
        Fixture fixture = fixture(true); UserEntity victim = user("Victim");
        Frames frames = subscribe(connect(fixture.sender()), fixture.room());
        StompSession session = sessions.getLast();
        session.send("/app/room/" + fixture.room().getId() + "/chat", Map.of(
                "clientMessageId", fixture.command().toString(), "content", "auth wins",
                "senderUserId", victim.getId()));
        JsonNode event = frames.await();
        assertThat(event.path("payload").path("sender").path("userId").asLong()).isEqualTo(fixture.sender().getId());
        assertThat(rowCount(fixture.room(), victim, fixture.command())).isZero();
    }

    @Test
    void idempotentRetryDoesNotRebroadcast() throws Exception {
        Fixture fixture = fixture(true); StompSession session = connect(fixture.sender());
        Frames frames = subscribe(session, fixture.room());
        session.send("/app/room/" + fixture.room().getId() + "/chat",
                new ChatMessageCommand(fixture.command().toString(), " retry "));
        JsonNode first = frames.await();
        session.send("/app/room/" + fixture.room().getId() + "/chat",
                new ChatMessageCommand(fixture.command().toString(), "retry"));
        assertThat(frames.poll()).isNull();
        assertThat(rowCount(fixture.room(), fixture.sender(), fixture.command())).isOne();
        assertThat(jdbc.queryForObject("SELECT id FROM chat_messages WHERE room_id=? AND sender_user_id=? AND client_message_id=?",
                Long.class, fixture.room().getId(), fixture.sender().getId(), fixture.command().toString()))
                .isEqualTo(first.path("payload").path("messageId").asLong());
    }

    @Test
    void conflictingRetryLeavesOriginalAndDoesNotRebroadcast() throws Exception {
        Fixture fixture = fixture(true); StompSession session = connect(fixture.sender()); Frames frames = subscribe(session, fixture.room());
        session.send("/app/room/" + fixture.room().getId() + "/chat",
                new ChatMessageCommand(fixture.command().toString(), "original"));
        frames.await();
        session.send("/app/room/" + fixture.room().getId() + "/chat",
                new ChatMessageCommand(fixture.command().toString(), "changed"));
        assertThat(frames.poll()).isNull(); assertThat(rowCount(fixture.room(), fixture.sender(), fixture.command())).isOne();
        assertThat(jdbc.queryForObject("SELECT content FROM chat_messages WHERE room_id=? AND sender_user_id=? AND client_message_id=?",
                String.class, fixture.room().getId(), fixture.sender().getId(), fixture.command().toString())).isEqualTo("original");
    }

    @Test
    void rolledBackMessageIsNeverBroadcast() throws Exception {
        Fixture fixture = fixture(true); Frames frames = subscribe(connect(fixture.sender()), fixture.room());
        jdbc.update("DELETE FROM player_profiles WHERE user_id=?", fixture.sender().getId());
        send(fixture.sender(), fixture.room(), fixture.command(), "rollback");
        assertThat(frames.poll()).isNull(); assertThat(rowCount(fixture.room(), fixture.sender(), fixture.command())).isZero();
    }

    private void assertEnvelope(JsonNode event, RoomEntity room, UUID command, UserEntity sender, String content) {
        assertThat(event.path("protocolVersion").asInt()).isEqualTo(1);
        assertThat(event.path("eventId").asText()).isNotBlank().isNotEqualTo(command.toString());
        assertThat(event.path("type").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(event.path("occurredAt").asText()).isNotBlank();
        assertThat(event.path("scope").path("roomId").asLong()).isEqualTo(room.getId());
        JsonNode message = event.path("payload");
        assertThat(message.path("messageId").asLong()).isPositive();
        assertThat(message.path("roomId").asLong()).isEqualTo(room.getId());
        assertThat(message.path("clientMessageId").asText()).isEqualTo(command.toString());
        assertThat(message.path("content").asText()).isEqualTo(content);
        assertThat(message.path("createdAt").asText()).isNotBlank();
        assertThat(message.path("sender").path("userId").asLong()).isEqualTo(sender.getId());
        assertThat(message.path("sender").path("displayName").asText()).isEqualTo("Alpha");
    }

    private Frames subscribe(StompSession session, RoomEntity room) throws Exception {
        Frames frames = new Frames(json); String id = UUID.randomUUID().toString();
        String destination = "/topic/room/" + room.getId();
        CompletableFuture<Void> handled = subscriptions.await(id, destination);
        StompHeaders headers = new StompHeaders(); headers.setId(id); headers.setDestination(destination);
        session.subscribe(headers, frames); handled.get(5, TimeUnit.SECONDS); return frames;
    }

    private StompSession connect(UserEntity user) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(List.of(
                new ByteArrayMessageConverter(), new MappingJackson2MessageConverter())));
        clients.add(client);
        StompHeaders headers = new StompHeaders(); headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(user));
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessions.add(session); return session;
    }

    private void send(UserEntity sender, RoomEntity room, UUID command, String content) throws Exception {
        StompSession session = connect(sender);
        session.send("/app/room/" + room.getId() + "/chat", new ChatMessageCommand(command.toString(), content));
    }

    private Fixture fixture(boolean seated) {
        UserEntity sender = user("Alpha");
        RoomEntity room = rooms.saveAndFlush(new RoomEntity("chat-ws-" + UUID.randomUUID(), sender.getId(),
                RoomType.PUBLIC, null, 6, 5, 10, 100, RoomStatus.WAITING,
                Instant.parse("2026-08-30T01:00:00Z")));
        roomIds.add(room.getId()); join(room, sender, seated ? 1 : null);
        return new Fixture(room, sender, UUID.randomUUID());
    }

    private UserEntity user(String displayName) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = users.saveAndFlush(new UserEntity("chat-ws-" + suffix, "test-hash", null,
                Role.PLAYER, AccountStatus.ACTIVE, 0)); userIds.add(user.getId());
        profiles.saveAndFlush(new PlayerProfileEntity(user.getId(), displayName, null, PresenceStatus.OFFLINE));
        return user;
    }

    private void join(RoomEntity room, UserEntity user, Integer seat) {
        members.saveAndFlush(new RoomPlayerEntity(room.getId(), user.getId(), seat,
                seat == null ? RoomPlayerState.SPECTATING : RoomPlayerState.NOT_READY, seat == null ? 0 : 100));
    }

    private int rowCount(RoomEntity room, UserEntity sender, UUID command) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM chat_messages
                WHERE room_id=? AND sender_user_id=? AND client_message_id=?
                """, Integer.class, room.getId(), sender.getId(), command.toString());
    }

    private static void disconnectQuietly(StompSession session) {
        if (session == null) return;
        try { if (session.isConnected()) session.disconnect(); }
        catch (MessageDeliveryException | IllegalStateException failure) {
            if (!alreadyClosed(failure)) throw failure;
        }
    }

    private static boolean alreadyClosed(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != current) {
            if (current instanceof IllegalStateException && current.getMessage() != null
                    && current.getMessage().equalsIgnoreCase("Connection closed")) return true;
            if (current instanceof IllegalStateException && java.util.Arrays.stream(current.getStackTrace())
                    .anyMatch(frame -> frame.getClassName().equals("org.apache.tomcat.websocket.WsSession")
                            && frame.getMethodName().equals("checkState"))) return true;
            current = current.getCause();
        }
        return current instanceof IllegalStateException && current.getMessage() != null
                && current.getMessage().equalsIgnoreCase("Connection closed");
    }

    private record Fixture(RoomEntity room, UserEntity sender, UUID command) { }

    private static final class Frames implements StompFrameHandler {
        private final BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>(); private final ObjectMapper json;
        private Frames(ObjectMapper json) { this.json = json; }
        @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) { frames.add((byte[]) payload); }
        JsonNode await() throws Exception {
            byte[] payload = frames.poll(5, TimeUnit.SECONDS);
            assertThat(payload).as("CHAT_MESSAGE frame").isNotNull(); return json.readTree(payload);
        }
        byte[] poll() throws InterruptedException { return frames.poll(500, TimeUnit.MILLISECONDS); }
    }
}
