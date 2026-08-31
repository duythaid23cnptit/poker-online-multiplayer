package com.ptit.poker.social.presence.application;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.game.application.realtime.RealtimeConnectionRegistry;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileEntity;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
import com.ptit.poker.social.application.FriendshipService;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
class PresenceMySqlIntegrationTests {
    @Autowired PresenceService presence;
    @Autowired PresencePersistencePort persistence;
    @Autowired PresenceStartupReconciler startup;
    @Autowired AcceptedFriendQueryPort acceptedFriends;
    @Autowired FriendshipService friendships;
    @Autowired RealtimeConnectionRegistry connections;
    @Autowired UserRepository users;
    @Autowired PlayerProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach void cleanup() {
        for (long id : userIds) jdbc.update("DELETE FROM friendships WHERE requester_user_id=? OR recipient_user_id=?", id, id);
        for (long id : userIds) jdbc.update("DELETE FROM player_profiles WHERE user_id=?", id);
        for (long id : userIds) jdbc.update("DELETE FROM users WHERE id=?", id);
    }

    @Test void firstAuthenticatedConnectionMarksProfileOnline() {
        UserEntity user = user("Alpha", PresenceStatus.OFFLINE);
        assertThat(presence.online(user.getId())).isTrue();
        assertThat(persistence.find(user.getId())).contains(PresenceStatus.ONLINE);
    }

    @Test void lastDisconnectMarksProfileOffline() {
        UserEntity user = user("Alpha", PresenceStatus.OFFLINE); presence.online(user.getId());
        assertThat(presence.offline(user.getId())).isTrue();
        assertThat(persistence.find(user.getId())).contains(PresenceStatus.OFFLINE);
    }

    @Test void twoSessionsRemainOnlineUntilLastDisconnect() {
        UserEntity user = user("Alpha", PresenceStatus.OFFLINE);
        String first = "presence-" + UUID.randomUUID(), second = "presence-" + UUID.randomUUID();
        if (connections.register(first, user.getId())) presence.online(user.getId());
        if (connections.register(second, user.getId())) presence.online(user.getId());
        var partial = connections.unregister(first); if (partial.lastSession()) presence.offline(user.getId());
        assertThat(persistence.find(user.getId())).contains(PresenceStatus.ONLINE);
        var last = connections.unregister(second); if (last.lastSession()) presence.offline(user.getId());
        assertThat(persistence.find(user.getId())).contains(PresenceStatus.OFFLINE);
    }

    @Test void acceptedFriendListExposesOnlinePresence() {
        UserEntity a = user("Alpha", PresenceStatus.ONLINE), b = user("Beta", PresenceStatus.OFFLINE);
        friendship(a, b, "ACCEPTED");
        assertThat(friendships.listFriends(b.getId())).singleElement()
                .satisfies(view -> assertThat(view.presenceStatus()).isEqualTo(PresenceStatus.ONLINE));
    }

    @Test void acceptedFriendListExposesOfflinePresence() {
        UserEntity a = user("Alpha", PresenceStatus.OFFLINE), b = user("Beta", PresenceStatus.OFFLINE);
        friendship(a, b, "ACCEPTED");
        assertThat(friendships.listFriends(b.getId())).singleElement()
                .satisfies(view -> assertThat(view.presenceStatus()).isEqualTo(PresenceStatus.OFFLINE));
    }

    @Test void pendingFriendshipDoesNotQualifyForPresenceNotification() {
        UserEntity a = user("Alpha", PresenceStatus.OFFLINE), b = user("Beta", PresenceStatus.OFFLINE);
        friendship(a, b, "PENDING");
        assertThat(acceptedFriends.findAcceptedFriendIds(a.getId())).doesNotContain(b.getId());
    }

    @Test void rejectedFriendshipDoesNotQualifyForPresenceNotification() {
        UserEntity a = user("Alpha", PresenceStatus.OFFLINE), b = user("Beta", PresenceStatus.OFFLINE);
        friendship(a, b, "REJECTED");
        assertThat(acceptedFriends.findAcceptedFriendIds(a.getId())).doesNotContain(b.getId());
    }

    @Test void startupReconciliationClearsStaleOnlineProjection() {
        UserEntity user = user("Alpha", PresenceStatus.ONLINE);
        startup.reconcile();
        assertThat(persistence.find(user.getId())).contains(PresenceStatus.OFFLINE);
    }

    private UserEntity user(String displayName, PresenceStatus status) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = users.saveAndFlush(new UserEntity("presence-" + suffix, "test-hash", null,
                Role.PLAYER, AccountStatus.ACTIVE, 0)); userIds.add(user.getId());
        profiles.saveAndFlush(new PlayerProfileEntity(user.getId(), displayName, null, status)); return user;
    }

    private void friendship(UserEntity requester, UserEntity recipient, String status) {
        jdbc.update("""
                INSERT INTO friendships (requester_user_id, recipient_user_id, status, created_at,
                                         responded_at, version)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6),
                        CASE WHEN ?='PENDING' THEN NULL ELSE UTC_TIMESTAMP(6) END, 0)
                """, requester.getId(), recipient.getId(), status, status);
    }
}
