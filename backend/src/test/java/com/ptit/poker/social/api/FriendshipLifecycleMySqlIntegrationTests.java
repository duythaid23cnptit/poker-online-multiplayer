package com.ptit.poker.social.api;

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
import com.ptit.poker.social.application.FriendshipSendResult;
import com.ptit.poker.social.application.FriendshipService;
import com.ptit.poker.social.application.FriendshipView;
import com.ptit.poker.social.domain.FriendshipStatus;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
@AutoConfigureMockMvc
class FriendshipLifecycleMySqlIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PlayerProfileRepository profiles;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired FriendshipService service;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (long userId : userIds) {
            jdbc.update("DELETE FROM friendships WHERE lower_user_id=? OR higher_user_id=?", userId, userId);
        }
        for (long userId : userIds) jdbc.update("DELETE FROM player_profiles WHERE user_id=?", userId);
        for (long userId : userIds) jdbc.update("DELETE FROM users WHERE id=?", userId);
    }

    @Test
    void allFriendshipEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/friend-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/friend-requests").queryParam("direction", "incoming"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/friend-requests/1/accept")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/friend-requests/1/reject")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/friends")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/friends/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void sendNewUsesPrincipalCreatesPendingAndReturnsOnlySafeProjection() throws Exception {
        UserEntity requester = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity recipient = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        UserEntity spoofed = user(AccountStatus.ACTIVE, Role.PLAYER, "Gamma");

        MvcResult response = mvc.perform(post("/api/v1/friend-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\":" + recipient.getId()
                                + ",\"requesterUserId\":" + spoofed.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.otherPlayer.userId").value(recipient.getId()))
                .andExpect(jsonPath("$.presenceStatus").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.accountChips").doesNotExist())
                .andReturn();

        JsonNode body = json.readTree(response.getResponse().getContentAsString());
        long requestId = body.get("requestId").asLong();
        assertThat(row(requestId).get("requester_user_id")).isEqualTo(requester.getId());
        assertThat(row(requestId).get("recipient_user_id")).isEqualTo(recipient.getId());
        assertThat(row(requestId).get("lower_user_id")).isEqualTo(Math.min(requester.getId(), recipient.getId()));
        assertThat(row(requestId).get("higher_user_id")).isEqualTo(Math.max(requester.getId(), recipient.getId()));
        assertThat(row(requestId).get("responded_at")).isNull();
    }

    @Test
    void selfRequestIsConflictWithoutRow() throws Exception {
        UserEntity user = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        send(user, user.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_FRIEND_REQUEST"));
        assertThat(pairCount(user.getId(), user.getId())).isZero();
    }

    @Test
    void inactiveTargetIsConcealedAsPlayerNotFound() throws Exception {
        UserEntity requester = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity target = user(AccountStatus.LOCKED, Role.PLAYER, "Beta");
        send(requester, target.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        assertThat(pairCount(requester.getId(), target.getId())).isZero();
    }

    @Test
    void sameDirectionDuplicateIsConflictAndKeepsOneRow() throws Exception {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        send(a, b.getId()).andExpect(status().isCreated());
        send(a, b.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_ALREADY_EXISTS"));
        assertThat(pairCount(a.getId(), b.getId())).isOne();
    }

    @Test
    void crossedRequestAutoAcceptsSameRow() throws Exception {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        long id = service.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();

        send(b, a.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(id))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.presenceStatus").doesNotExist())
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());
        assertThat(pairCount(a.getId(), b.getId())).isOne();
    }

    @Test
    void rejectedRowReopensInPlaceWithNewDirection() {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        FriendshipView pending = service.sendFriendRequest(a.getId(), b.getId()).friendship();
        service.rejectFriendRequest(b.getId(), pending.requestId());

        FriendshipView reopened = service.sendFriendRequest(b.getId(), a.getId()).friendship();

        assertThat(reopened.requestId()).isEqualTo(pending.requestId());
        assertThat(reopened.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(row(pending.requestId())).containsEntry("requester_user_id", b.getId())
                .containsEntry("recipient_user_id", a.getId()).containsEntry("status", "PENDING")
                .containsEntry("version", 2L);
        assertThat(row(pending.requestId()).get("responded_at")).isNull();
        assertThat(pairCount(a.getId(), b.getId())).isOne();
    }

    @Test
    void acceptRequiresCurrentRecipientAndBecomesNonPending() throws Exception {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        long requestId = service.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        mvc.perform(post("/api/v1/friend-requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(a)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_AUTHORIZED"));
        mvc.perform(post("/api/v1/friend-requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.presenceStatus").doesNotExist());
        mvc.perform(post("/api/v1/friend-requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(b)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_PENDING"));
    }

    @Test
    void rejectRequiresCurrentRecipient() throws Exception {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        UserEntity c = user(AccountStatus.ACTIVE, Role.ADMIN, "Gamma");
        long requestId = service.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        mvc.perform(post("/api/v1/friend-requests/{id}/reject", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(c)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_AUTHORIZED"));
        mvc.perform(post("/api/v1/friend-requests/{id}/reject", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.presenceStatus").doesNotExist());
    }

    @Test
    void requestListsArePendingDirectionalDeterministicAndInvalidDirectionIsBadRequest() throws Exception {
        UserEntity current = user(AccountStatus.ACTIVE, Role.PLAYER, "Current");
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        service.sendFriendRequest(a.getId(), current.getId());
        service.sendFriendRequest(current.getId(), b.getId());
        long rejected = service.sendFriendRequest(b.getId(), a.getId()).friendship().requestId();
        service.rejectFriendRequest(a.getId(), rejected);

        mvc.perform(get("/api/v1/friend-requests").queryParam("direction", "incoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(current)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].otherPlayer.userId").value(a.getId()))
                .andExpect(jsonPath("$[0].presenceStatus").doesNotExist())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/friend-requests").queryParam("direction", "outgoing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(current)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].otherPlayer.userId").value(b.getId()))
                .andExpect(jsonPath("$[0].presenceStatus").doesNotExist())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/friend-requests").queryParam("direction", "sideways")
                        .header(HttpHeaders.AUTHORIZATION, bearer(current)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void friendListIsSymmetricAcceptedOnlyAndSorted() throws Exception {
        UserEntity current = user(AccountStatus.ACTIVE, Role.PLAYER, "Current");
        UserEntity zed = user(AccountStatus.ACTIVE, Role.PLAYER, "Zed");
        UserEntity alpha = user(AccountStatus.ACTIVE, Role.PLAYER, "alpha");
        UserEntity pending = user(AccountStatus.ACTIVE, Role.PLAYER, "Pending");
        accept(current, zed);
        accept(alpha, current);
        service.sendFriendRequest(current.getId(), pending.getId());

        mvc.perform(get("/api/v1/friends").header(HttpHeaders.AUTHORIZATION, bearer(current)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].otherPlayer.userId").value(alpha.getId()))
                .andExpect(jsonPath("$[0].presenceStatus").value("OFFLINE"))
                .andExpect(jsonPath("$[1].otherPlayer.userId").value(zed.getId()))
                .andExpect(jsonPath("$[1].presenceStatus").value("OFFLINE"));
        assertThat(service.listFriends(zed.getId())).extracting(view -> view.otherPlayer().userId())
                .containsExactly(current.getId());
    }

    @Test
    void removeUsesOtherUserIdDeletesSymmetricallyAndRetryIsNotFound() throws Exception {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        accept(a, b);
        mvc.perform(delete("/api/v1/friends/{friendId}", b.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(a)))
                .andExpect(status().isNoContent());
        assertThat(service.listFriends(b.getId())).isEmpty();
        mvc.perform(delete("/api/v1/friends/{friendId}", b.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(a)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FRIENDSHIP_NOT_FOUND"));
    }

    @Test
    void concurrentSameDirectionSendHasOneSuccessOneConflictAndOnePendingRow() throws Exception {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        List<Object> results = race(() -> service.sendFriendRequest(a.getId(), b.getId()),
                () -> service.sendFriendRequest(a.getId(), b.getId()));
        assertThat(results).filteredOn(FriendshipSendResult.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(result -> hasCode(result, "FRIEND_REQUEST_ALREADY_EXISTS")).hasSize(1);
        assertPair(a, b, "PENDING", 1);
    }

    @Test
    void concurrentCrossedSendFinishesAcceptedWithOneRow() throws Exception {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        List<Object> results = race(() -> service.sendFriendRequest(a.getId(), b.getId()),
                () -> service.sendFriendRequest(b.getId(), a.getId()));
        assertThat(results).allMatch(FriendshipSendResult.class::isInstance);
        assertPair(a, b, "ACCEPTED", 1);
    }

    @Test
    void concurrentAcceptRejectAllowsExactlyOneTransition() throws Exception {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        long requestId = service.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        List<Object> results = race(() -> service.acceptFriendRequest(b.getId(), requestId),
                () -> service.rejectFriendRequest(b.getId(), requestId));
        assertThat(results).filteredOn(FriendshipView.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(result -> hasCode(result, "FRIEND_REQUEST_NOT_PENDING")).hasSize(1);
        assertThat(row(requestId).get("status")).isIn("ACCEPTED", "REJECTED");
        assertThat(row(requestId).get("version")).isEqualTo(1L);
    }

    @Test
    void mappingFailureAfterTransitionRollsBackTransaction() {
        UserEntity a = user(AccountStatus.ACTIVE, Role.PLAYER, "Alpha");
        UserEntity b = user(AccountStatus.ACTIVE, Role.PLAYER, "Beta");
        long requestId = service.sendFriendRequest(a.getId(), b.getId()).friendship().requestId();
        jdbc.update("DELETE FROM player_profiles WHERE user_id=?", a.getId());

        assertThatThrownBy(() -> service.acceptFriendRequest(b.getId(), requestId))
                .isInstanceOf(FriendshipException.class);
        assertThat(row(requestId)).containsEntry("status", "PENDING").containsEntry("version", 0L);
    }

    private org.springframework.test.web.servlet.ResultActions send(UserEntity requester, long recipientId) throws Exception {
        return mvc.perform(post("/api/v1/friend-requests").header(HttpHeaders.AUTHORIZATION, bearer(requester))
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipientUserId\":" + recipientId + "}"));
    }

    private FriendshipView accept(UserEntity requester, UserEntity recipient) {
        FriendshipView pending = service.sendFriendRequest(requester.getId(), recipient.getId()).friendship();
        return service.acceptFriendRequest(recipient.getId(), pending.requestId());
    }

    private List<Object> race(ThrowingSupplier first, ThrowingSupplier second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> one = executor.submit(() -> runRacer(first, ready, start));
            Future<Object> two = executor.submit(() -> runRacer(second, ready, start));
            ready.await();
            start.countDown();
            return Arrays.asList(one.get(), two.get());
        }
    }

    private static Object runRacer(ThrowingSupplier action, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return action.get();
        } catch (Throwable failure) {
            return failure;
        }
    }

    private UserEntity user(AccountStatus status, Role role, String displayName) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserEntity user = users.saveAndFlush(new UserEntity(
                "s10_" + suffix, "test-hash", "s10_" + suffix + "@example.test", role, status, 0));
        userIds.add(user.getId());
        profiles.saveAndFlush(new PlayerProfileEntity(user.getId(), displayName, null, PresenceStatus.OFFLINE));
        return user;
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwt.createAccessToken(user);
    }

    private java.util.Map<String, Object> row(long requestId) {
        return jdbc.queryForMap("""
                SELECT requester_user_id, recipient_user_id, lower_user_id, higher_user_id,
                       status, responded_at, version
                FROM friendships WHERE id=?
                """, requestId);
    }

    private long pairCount(long a, long b) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM friendships
                WHERE lower_user_id=LEAST(?, ?) AND higher_user_id=GREATEST(?, ?)
                """, Long.class, a, b, a, b);
    }

    private void assertPair(UserEntity a, UserEntity b, String status, long count) {
        assertThat(pairCount(a.getId(), b.getId())).isEqualTo(count);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM friendships
                WHERE lower_user_id=LEAST(?, ?) AND higher_user_id=GREATEST(?, ?)
                """, String.class, a.getId(), b.getId(), a.getId(), b.getId())).isEqualTo(status);
    }

    private static boolean hasCode(Object result, String code) {
        return result instanceof FriendshipException exception && exception.code().equals(code);
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }
}
