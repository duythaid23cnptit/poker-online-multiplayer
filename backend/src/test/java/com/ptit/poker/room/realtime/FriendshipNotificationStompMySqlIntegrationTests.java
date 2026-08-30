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
import com.ptit.poker.social.application.FriendshipException;
import com.ptit.poker.social.application.FriendshipService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbeConfiguration.class)
class FriendshipNotificationStompMySqlIntegrationTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PlayerProfileRepository profiles;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired FriendshipService friendships;
    @Autowired RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbe subscriptions;

    private final List<Long> userIds = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach
    void cleanup() {
        sessions.forEach(session -> { if (session.isConnected()) session.disconnect(); });
        clients.forEach(WebSocketStompClient::stop);
        subscriptions.reset();
        for (long id : userIds) jdbc.update("DELETE FROM friendships WHERE lower_user_id=? OR higher_user_id=?", id, id);
        for (long id : userIds) jdbc.update("DELETE FROM player_profiles WHERE user_id=?", id);
        for (long id : userIds) jdbc.update("DELETE FROM users WHERE id=?", id);
    }

    @Test
    void requestIsPrivateSafeAfterCommitAndExactlyOnce() throws Exception {
        UserEntity a = user("Alpha"); UserEntity b = user("Beta"); UserEntity c = user("Gamma");
        Frames aFrames = subscribe(connect(a)); Frames bFrames = subscribe(connect(b)); Frames cFrames = subscribe(connect(c));

        long requestId = friendships.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();

        JsonNode event = bFrames.await("FRIEND_REQUEST_RECEIVED");
        assertEnvelope(event, requestId, a);
        assertThat(event.toString()).doesNotContain("password", "email", "role", "accountChips");
        assertThat(aFrames.poll()).isNull(); assertThat(cFrames.poll()).isNull(); assertThat(bFrames.poll()).isNull();
        assertThat(status(requestId)).isEqualTo("PENDING");
    }

    @Test
    void acceptRejectAndRemoveNotifyOnlyTheOtherParticipant() throws Exception {
        UserEntity a = user("Alpha"); UserEntity b = user("Beta");
        Frames aFrames = subscribe(connect(a)); Frames bFrames = subscribe(connect(b));
        long accepted = friendships.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        bFrames.await("FRIEND_REQUEST_RECEIVED");
        friendships.acceptFriendRequest(b.getId(), accepted);
        assertEnvelope(aFrames.await("FRIEND_REQUEST_ACCEPTED"), accepted, b);
        friendships.removeFriend(b.getId(), a.getId());
        JsonNode removed = aFrames.await("FRIEND_REMOVED");
        assertEnvelope(removed, null, b);
        assertThat(bFrames.poll()).isNull();

        long rejected = friendships.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        bFrames.await("FRIEND_REQUEST_RECEIVED");
        friendships.rejectFriendRequest(b.getId(), rejected);
        assertEnvelope(aFrames.await("FRIEND_REQUEST_REJECTED"), rejected, b);
    }

    @Test
    void crossedAndReopenedRequestsUseTheirSpecificSemantics() throws Exception {
        UserEntity a = user("Alpha"); UserEntity b = user("Beta");
        Frames aFrames = subscribe(connect(a)); Frames bFrames = subscribe(connect(b));
        long id = friendships.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        bFrames.await("FRIEND_REQUEST_RECEIVED");
        friendships.sendFriendRequest(b.getId(), a.getId());
        assertEnvelope(aFrames.await("FRIEND_REQUEST_ACCEPTED"), id, b);
        assertThat(bFrames.poll()).isNull();
        friendships.removeFriend(a.getId(), b.getId()); bFrames.await("FRIEND_REMOVED");

        long rejected = friendships.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        bFrames.await("FRIEND_REQUEST_RECEIVED"); friendships.rejectFriendRequest(b.getId(), rejected);
        aFrames.await("FRIEND_REQUEST_REJECTED");
        friendships.sendFriendRequest(b.getId(), a.getId());
        assertEnvelope(aFrames.await("FRIEND_REQUEST_RECEIVED"), rejected, b);
    }

    @Test
    void conflictsAuthorizationFailuresAndRollbackEmitNothing() throws Exception {
        UserEntity a = user("Alpha"); UserEntity b = user("Beta"); UserEntity c = user("Gamma");
        Frames aFrames = subscribe(connect(a)); Frames bFrames = subscribe(connect(b));
        long id = friendships.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        bFrames.await("FRIEND_REQUEST_RECEIVED");
        assertThatThrownBy(() -> friendships.sendFriendRequest(a.getId(), b.getId())).isInstanceOf(FriendshipException.class);
        assertThatThrownBy(() -> friendships.acceptFriendRequest(c.getId(), id)).isInstanceOf(FriendshipException.class);
        assertThat(aFrames.poll()).isNull(); assertThat(bFrames.poll()).isNull();

        jdbc.update("DELETE FROM player_profiles WHERE user_id=?", a.getId());
        assertThatThrownBy(() -> friendships.acceptFriendRequest(b.getId(), id)).isInstanceOf(FriendshipException.class);
        assertThat(aFrames.poll()).isNull(); assertThat(status(id)).isEqualTo("PENDING");
    }

    @Test
    void offlineRecipientRecoversAuthoritativePendingState() {
        UserEntity a = user("Alpha"); UserEntity b = user("Beta");
        long id = friendships.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        assertThat(friendships.listFriendRequests(b.getId(), com.ptit.poker.social.application.FriendRequestDirection.INCOMING))
                .extracting(view -> view.requestId()).containsExactly(id);
    }

    private Frames subscribe(StompSession session) throws Exception {
        Frames frames = new Frames(json);
        String id = UUID.randomUUID().toString();
        CompletableFuture<Void> handled = subscriptions.await(id, "/user/queue/notifications");
        StompHeaders headers = new StompHeaders(); headers.setId(id); headers.setDestination("/user/queue/notifications");
        session.subscribe(headers, frames); handled.get(5, TimeUnit.SECONDS);
        return frames;
    }

    private StompSession connect(UserEntity user) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(List.of(new ByteArrayMessageConverter(), new MappingJackson2MessageConverter())));
        clients.add(client);
        StompHeaders headers = new StompHeaders(); headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(user));
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessions.add(session); return session;
    }

    private UserEntity user(String displayName) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserEntity user = users.saveAndFlush(new UserEntity("s10n_" + suffix, "test-hash", null,
                Role.PLAYER, AccountStatus.ACTIVE, 0));
        userIds.add(user.getId());
        profiles.saveAndFlush(new PlayerProfileEntity(user.getId(), displayName, null, PresenceStatus.OFFLINE));
        return user;
    }

    private String status(long id) { return jdbc.queryForObject("SELECT status FROM friendships WHERE id=?", String.class, id); }

    private static void assertEnvelope(JsonNode event, Long requestId, UserEntity actor) {
        assertThat(event.path("protocolVersion").asInt()).isEqualTo(1);
        assertThat(event.path("eventId").asText()).isNotBlank();
        assertThat(event.path("occurredAt").asText()).isNotBlank();
        if (requestId == null) assertThat(event.path("payload").path("requestId").isNull()).isTrue();
        else assertThat(event.path("payload").path("requestId").asLong()).isEqualTo(requestId);
        assertThat(event.path("payload").path("player").path("userId").asLong()).isEqualTo(actor.getId());
    }

    private static final class Frames implements StompFrameHandler {
        private final BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>(); private final ObjectMapper json;
        private Frames(ObjectMapper json) { this.json = json; }
        @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) { frames.add((byte[]) payload); }
        JsonNode await(String type) throws Exception {
            byte[] payload = frames.poll(5, TimeUnit.SECONDS);
            assertThat(payload).as("notification " + type).isNotNull();
            JsonNode event = json.readTree(payload); assertThat(event.path("type").asText()).isEqualTo(type); return event;
        }
        byte[] poll() throws InterruptedException { return frames.poll(300, TimeUnit.MILLISECONDS); }
    }
}
