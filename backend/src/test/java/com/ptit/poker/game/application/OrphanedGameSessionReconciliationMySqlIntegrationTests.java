package com.ptit.poker.game.application;

import com.ptit.poker.admin.application.AdminQueryService;
import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.game.infrastructure.persistence.GameSessionEntity;
import com.ptit.poker.game.infrastructure.persistence.GameSessionRepository;
import com.ptit.poker.game.infrastructure.persistence.GameSessionStatus;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileEntity;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
import com.ptit.poker.room.application.RoomApplicationService;
import com.ptit.poker.room.domain.RoomPlayerState;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
class OrphanedGameSessionReconciliationMySqlIntegrationTests {
    @Autowired OrphanedGameSessionReconciliationService reconciliation;
    @Autowired GameSessionRepository sessions;
    @Autowired RoomRepository rooms;
    @Autowired RoomPlayerRepository members;
    @Autowired UserRepository users;
    @Autowired PlayerProfileRepository profiles;
    @Autowired AdminQueryService admin;
    @Autowired RoomApplicationService roomApplication;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> sessionIds = new ArrayList<>();
    private final List<Long> roomIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (long id : sessionIds) jdbc.update("DELETE FROM game_sessions WHERE id=?", id);
        for (long id : roomIds) jdbc.update("DELETE FROM room_players WHERE room_id=?", id);
        for (long id : roomIds) jdbc.update("DELETE FROM rooms WHERE id=?", id);
        for (long id : userIds) jdbc.update("DELETE FROM player_profiles WHERE user_id=?", id);
        for (long id : userIds) jdbc.update("DELETE FROM users WHERE id=?", id);
    }

    @Test
    void activeSessionRefundsEveryStackAndIsIdempotent() {
        UserEntity first = user(100);
        UserEntity second = user(200);
        RoomEntity room = room(first, RoomStatus.PLAYING);
        RoomPlayerEntity one = member(room, first, 1, 600);
        RoomPlayerEntity two = member(room, second, 2, 400);
        GameSessionEntity session = session(room, GameSessionStatus.ACTIVE, null);
        long conservedBefore = total(first, second, one, two);

        var result = reconciliation.reconcile();
        long firstBalance = users.findById(first.getId()).orElseThrow().getAccountChips();
        long secondBalance = users.findById(second.getId()).orElseThrow().getAccountChips();
        var repeated = reconciliation.reconcile();

        GameSessionEntity storedSession = sessions.findById(session.getId()).orElseThrow();
        RoomEntity storedRoom = rooms.findById(room.getId()).orElseThrow();
        RoomPlayerEntity storedOne = members.findById(one.getId()).orElseThrow();
        RoomPlayerEntity storedTwo = members.findById(two.getId()).orElseThrow();
        List<RoomPlayerEntity> storedMembers = members.findAllByRoomIdAndLeftAtIsNullOrderById(room.getId());
        assertThat(result.sessionsAborted()).isEqualTo(1);
        assertThat(result.membershipsFinalized()).isEqualTo(2);
        assertThat(result.chipsRefunded()).isEqualTo(1_000);
        assertThat(storedSession.getStatus()).isEqualTo(GameSessionStatus.ABORTED);
        assertThat(storedSession.getEndedAt()).isNotNull();
        assertThat(storedRoom.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(storedMembers).isEmpty();
        assertFinalized(storedOne);
        assertFinalized(storedTwo);
        assertThat(firstBalance).isEqualTo(700);
        assertThat(secondBalance).isEqualTo(600);
        assertThat(firstBalance + secondBalance).isEqualTo(conservedBefore);
        assertThat(repeated.sessionsAborted()).isZero();
        assertThat(users.findById(first.getId()).orElseThrow().getAccountChips()).isEqualTo(firstBalance);
        assertThat(users.findById(second.getId()).orElseThrow().getAccountChips()).isEqualTo(secondBalance);
        assertThat(admin.games(0, 20, "ACTIVE", room.getId(), null, null, null).items()).isEmpty();
        assertThat(roomApplication.list()).extracting(value -> value.id()).doesNotContain(room.getId());
    }

    @Test
    void activeSessionWithoutMembersStillAbortsAndFinishesRoom() {
        UserEntity owner = user(500);
        RoomEntity room = room(owner, RoomStatus.PLAYING);
        GameSessionEntity session = session(room, GameSessionStatus.ACTIVE, null);

        var result = reconciliation.reconcile();

        assertThat(result.sessionsAborted()).isEqualTo(1);
        assertThat(result.membershipsFinalized()).isZero();
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.ABORTED);
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.FINISHED);
    }

    @Test
    void terminalSessionsAndWaitingRoomWithoutActiveSessionAreUntouched() {
        UserEntity owner = user(300);
        RoomEntity finishedRoom = room(owner, RoomStatus.FINISHED);
        GameSessionEntity finished = session(finishedRoom, GameSessionStatus.FINISHED, Instant.parse("2026-09-01T01:00:00Z"));
        GameSessionEntity aborted = session(finishedRoom, GameSessionStatus.ABORTED, Instant.parse("2026-09-01T02:00:00Z"));
        RoomEntity waiting = room(owner, RoomStatus.WAITING);
        RoomPlayerEntity waitingMember = member(waiting, owner, 1, 250);

        var result = reconciliation.reconcile();

        assertThat(result.sessionsAborted()).isZero();
        assertThat(sessions.findById(finished.getId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.FINISHED);
        assertThat(sessions.findById(aborted.getId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.ABORTED);
        assertThat(rooms.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.WAITING);
        RoomPlayerEntity stored = members.findById(waitingMember.getId()).orElseThrow();
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.getTableChips()).isEqualTo(250);
        assertThat(users.findById(owner.getId()).orElseThrow().getAccountChips()).isEqualTo(300);
    }

    @Test
    void activeSessionCreatedAfterStartupRemainsHealthyUntilAnotherStartup() {
        UserEntity owner = user(500);
        RoomEntity room = room(owner, RoomStatus.PLAYING);
        GameSessionEntity healthy = session(room, GameSessionStatus.ACTIVE, null);

        assertThat(sessions.findById(healthy.getId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.ACTIVE);
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.PLAYING);
    }

    private UserEntity user(long accountChips) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserEntity user = users.saveAndFlush(new UserEntity("startup_" + suffix, "hash",
                suffix + "@example.test", Role.PLAYER, AccountStatus.ACTIVE, accountChips));
        userIds.add(user.getId());
        profiles.saveAndFlush(new PlayerProfileEntity(user.getId(), user.getUsername(), null, PresenceStatus.OFFLINE));
        return user;
    }

    private RoomEntity room(UserEntity owner, RoomStatus status) {
        RoomEntity room = rooms.saveAndFlush(new RoomEntity("startup-" + UUID.randomUUID(), owner.getId(),
                RoomType.PUBLIC, null, 6, 50, 100, 500, status, Instant.now()));
        roomIds.add(room.getId());
        return room;
    }

    private RoomPlayerEntity member(RoomEntity room, UserEntity user, int seat, long tableChips) {
        return members.saveAndFlush(new RoomPlayerEntity(
                room.getId(), user.getId(), seat, RoomPlayerState.PLAYING, tableChips));
    }

    private GameSessionEntity session(RoomEntity room, GameSessionStatus status, Instant endedAt) {
        GameSessionEntity session = sessions.saveAndFlush(new GameSessionEntity(
                room.getId(), UUID.randomUUID(), status, Instant.parse("2026-09-01T00:00:00Z"), endedAt));
        sessionIds.add(session.getId());
        return session;
    }

    private static long total(UserEntity first, UserEntity second, RoomPlayerEntity one, RoomPlayerEntity two) {
        return first.getAccountChips() + second.getAccountChips() + one.getTableChips() + two.getTableChips();
    }

    private static void assertFinalized(RoomPlayerEntity member) {
        assertThat(member.getTableChips()).isZero();
        assertThat(member.getSeatNumber()).isNull();
        assertThat(member.getState()).isEqualTo(RoomPlayerState.SPECTATING);
        assertThat(member.getLeftAt()).isNotNull();
    }
}
