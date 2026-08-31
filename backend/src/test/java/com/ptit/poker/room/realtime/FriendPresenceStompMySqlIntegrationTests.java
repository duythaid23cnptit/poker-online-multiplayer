package com.ptit.poker.room.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.game.application.realtime.RealtimeConnectionRegistry;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileEntity;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
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
import java.util.ArrayList;
import java.util.List;
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
class FriendPresenceStompMySqlIntegrationTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PlayerProfileRepository profiles;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired RealtimeConnectionRegistry connections;
    @Autowired RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbe subscriptions;
    private final List<Long> userIds = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach void cleanup() {
        sessions.forEach(FriendPresenceStompMySqlIntegrationTests::disconnectQuietly);
        clients.forEach(WebSocketStompClient::stop);
        awaitNoSessions(); subscriptions.reset();
        for (long id : userIds) jdbc.update("DELETE FROM friendships WHERE requester_user_id=? OR recipient_user_id=?", id, id);
        for (long id : userIds) jdbc.update("DELETE FROM player_profiles WHERE user_id=?", id);
        for (long id : userIds) jdbc.update("DELETE FROM users WHERE id=?", id);
    }

    @Test void firstConnectionNotifiesAcceptedFriendOnline() throws Exception {
        Pair pair = acceptedPair(); Frames friendFrames = subscribe(connect(pair.friend()));
        connect(pair.subject());
        assertPresence(friendFrames.await(), pair.subject(), "ONLINE");
        assertThat(status(pair.subject())).isEqualTo("ONLINE");
    }

    @Test void secondSessionDoesNotEmitSecondOnline() throws Exception {
        Pair pair = acceptedPair(); Frames frames = subscribe(connect(pair.friend()));
        connect(pair.subject()); frames.await(); connect(pair.subject());
        assertThat(frames.poll()).isNull(); assertThat(connections.activeSessionCount(pair.subject().getId())).isEqualTo(2);
    }

    @Test void disconnectingOneOfTwoSessionsDoesNotEmitOffline() throws Exception {
        Pair pair = acceptedPair(); Frames frames = subscribe(connect(pair.friend()));
        StompSession first = connect(pair.subject()); frames.await(); connect(pair.subject());
        first.disconnect();
        assertThat(frames.poll()).isNull(); assertThat(status(pair.subject())).isEqualTo("ONLINE");
    }

    @Test void finalDisconnectNotifiesAcceptedFriendOffline() throws Exception {
        Pair pair = acceptedPair(); Frames frames = subscribe(connect(pair.friend()));
        StompSession subject = connect(pair.subject()); frames.await(); subject.disconnect();
        assertPresence(frames.await(), pair.subject(), "OFFLINE");
        assertThat(status(pair.subject())).isEqualTo("OFFLINE");
    }

    @Test void reconnectEmitsOnlineAgainExactlyOnce() throws Exception {
        Pair pair = acceptedPair(); Frames frames = subscribe(connect(pair.friend()));
        StompSession first = connect(pair.subject()); frames.await(); first.disconnect(); frames.await();
        connect(pair.subject()); assertPresence(frames.await(), pair.subject(), "ONLINE");
        assertThat(frames.poll()).isNull();
    }

    @Test void nonFriendReceivesNoPresenceEvent() throws Exception {
        UserEntity subject = user("Alpha"), nonFriend = user("Gamma");
        Frames frames = subscribe(connect(nonFriend)); connect(subject);
        assertThat(frames.poll()).isNull(); assertThat(status(subject)).isEqualTo("ONLINE");
    }

    @Test void pendingFriendReceivesNoPresenceEvent() throws Exception {
        UserEntity subject = user("Alpha"), pending = user("Beta"); friendship(subject, pending, "PENDING");
        Frames frames = subscribe(connect(pending)); connect(subject);
        assertThat(frames.poll()).isNull(); assertThat(status(subject)).isEqualTo("ONLINE");
    }

    @Test void normalDisconnectCleanupIsIdempotentAndEndsOffline() throws Exception {
        Pair pair = acceptedPair(); Frames frames = subscribe(connect(pair.friend()));
        StompSession subject = connect(pair.subject()); frames.await(); subject.disconnect();
        assertPresence(frames.await(), pair.subject(), "OFFLINE");
        disconnectQuietly(subject);
        assertThat(status(pair.subject())).isEqualTo("OFFLINE");
        assertThat(frames.poll()).isNull();
    }

    private Frames subscribe(StompSession session) throws Exception {
        Frames frames = new Frames(json); String id = UUID.randomUUID().toString();
        CompletableFuture<Void> handled = subscriptions.await(id, "/user/queue/notifications");
        StompHeaders headers = new StompHeaders(); headers.setId(id); headers.setDestination("/user/queue/notifications");
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

    private Pair acceptedPair() {
        UserEntity subject = user("Alpha"), friend = user("Beta"); friendship(subject, friend, "ACCEPTED");
        return new Pair(subject, friend);
    }

    private UserEntity user(String displayName) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = users.saveAndFlush(new UserEntity("presence-ws-" + suffix, "test-hash", null,
                Role.PLAYER, AccountStatus.ACTIVE, 0)); userIds.add(user.getId());
        profiles.saveAndFlush(new PlayerProfileEntity(user.getId(), displayName, null, PresenceStatus.OFFLINE)); return user;
    }

    private void friendship(UserEntity requester, UserEntity recipient, String status) {
        jdbc.update("""
                INSERT INTO friendships (requester_user_id, recipient_user_id, status, created_at, responded_at, version)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6), CASE WHEN ?='PENDING' THEN NULL ELSE UTC_TIMESTAMP(6) END, 0)
                """, requester.getId(), recipient.getId(), status, status);
    }

    private String status(UserEntity user) {
        return jdbc.queryForObject("SELECT online_status FROM player_profiles WHERE user_id=?", String.class, user.getId());
    }

    private static void assertPresence(JsonNode event, UserEntity subject, String status) {
        assertThat(event.path("protocolVersion").asInt()).isEqualTo(1);
        assertThat(event.path("eventId").asText()).isNotBlank();
        assertThat(event.path("type").asText()).isEqualTo("FRIEND_STATUS_CHANGED");
        assertThat(event.path("occurredAt").asText()).isNotBlank();
        assertThat(event.path("payload").path("userId").asLong()).isEqualTo(subject.getId());
        assertThat(event.path("payload").path("presenceStatus").asText()).isEqualTo(status);
        assertThat(event.toString()).doesNotContain("session", "username", "email", "role", "chips", "token");
    }

    private void awaitNoSessions() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (userIds.stream().anyMatch(id -> connections.activeSessionCount(id) != 0)
                && System.nanoTime() < deadline) Thread.onSpinWait();
        assertThat(userIds).allSatisfy(id -> assertThat(connections.activeSessionCount(id)).isZero());
    }

    private static void disconnectQuietly(StompSession session) {
        if (session == null) return;
        try { if (session.isConnected()) session.disconnect(); }
        catch (MessageDeliveryException | IllegalStateException failure) {
            if (failure.getMessage() == null || !failure.getMessage().equalsIgnoreCase("Connection closed")) throw failure;
        }
    }

    private record Pair(UserEntity subject, UserEntity friend) { }
    private static final class Frames implements StompFrameHandler {
        private final BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>(); private final ObjectMapper json;
        private Frames(ObjectMapper json) { this.json = json; }
        @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) { frames.add((byte[]) payload); }
        JsonNode await() throws Exception {
            byte[] payload = frames.poll(5, TimeUnit.SECONDS);
            assertThat(payload).as("FRIEND_STATUS_CHANGED frame").isNotNull(); return json.readTree(payload);
        }
        byte[] poll() throws InterruptedException { return frames.poll(500, TimeUnit.MILLISECONDS); }
    }
}
