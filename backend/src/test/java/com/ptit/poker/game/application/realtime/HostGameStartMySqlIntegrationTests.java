package com.ptit.poker.game.application.realtime;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.game.application.runtime.GameRuntimeException;
import com.ptit.poker.game.infrastructure.persistence.GameSessionRepository;
import com.ptit.poker.room.api.dto.CreateRoomRequest;
import com.ptit.poker.room.api.dto.JoinRoomRequest;
import com.ptit.poker.room.application.RoomApplicationService;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest(properties = {"poker.game.turn-timeout=10m", "poker.game.inter-hand-delay=50ms"})
class HostGameStartMySqlIntegrationTests {
    @Autowired RoomApplicationService rooms;
    @Autowired UserRepository users;
    @Autowired RoomRepository roomRepository;
    @Autowired GameSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired GameRealtimeApplicationService games;
    @MockitoBean GameRealtimePublisher publisher;
    @MockitoBean RoomGameDiscoveryPublisher discovery;

    @Test
    void eachReadinessChangeIncludingTheFinalReadyLeavesTheRoomWaitingWithoutASession() {
        UserEntity owner = user();
        UserEntity first = user();
        UserEntity second = user();
        long roomId = create(owner);
        rooms.join(first.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        rooms.join(second.getId(), roomId, new JoinRoomRequest(false, 2, 100L, null));

        rooms.setReady(first.getId(), roomId, true);
        assertWaitingWithoutSession(roomId);

        rooms.setReady(second.getId(), roomId, true);
        assertThat(rooms.detail(owner.getId(), roomId).members().stream()
                .filter(member -> member.seatNumber() != null))
                .allMatch(member -> member.state() == com.ptit.poker.room.domain.RoomPlayerState.READY);
        assertWaitingWithoutSession(roomId);
    }

    @Test
    void readyToNotReadyToReadyNeverStartsAGame() {
        Fixture fixture = readyFixture();

        rooms.setReady(fixture.second().getId(), fixture.roomId(), false);
        assertWaitingWithoutSession(fixture.roomId());
        rooms.setReady(fixture.second().getId(), fixture.roomId(), true);
        assertWaitingWithoutSession(fixture.roomId());
    }

    @Test
    void spectatorHostCanTakeASeatAndReadyWithoutStarting() {
        UserEntity owner = user();
        UserEntity second = user();
        long roomId = create(owner);

        rooms.join(owner.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        rooms.join(second.getId(), roomId, new JoinRoomRequest(false, 2, 100L, null));
        rooms.setReady(owner.getId(), roomId, true);
        rooms.setReady(second.getId(), roomId, true);

        assertWaitingWithoutSession(roomId);
        assertThat(games.startGame(roomId, owner.getId()).players()).hasSize(2);
        assertThat(sessions.findAllByRoomIdOrderByStartedAtDesc(roomId)).hasSize(1);
    }

    @Test
    void readyPlayersRemainWaitingUntilTheHostStarts() {
        Fixture fixture = readyFixture();

        assertThat(sessions.findAllByRoomIdOrderByStartedAtDesc(fixture.roomId())).isEmpty();
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.WAITING);
    }

    @Test
    void spectatorHostStartsTwoReadyPlayersExactlyOnce() {
        Fixture fixture = readyFixture();

        games.startGame(fixture.roomId(), fixture.owner().getId());

        assertThat(sessions.findAllByRoomIdOrderByStartedAtDesc(fixture.roomId())).hasSize(1);
        assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.PLAYING);
    }

    @Test
    void seatedReadyHostCanStart() {
        UserEntity owner = user();
        UserEntity second = user();
        long roomId = create(owner);
        rooms.join(owner.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        rooms.join(second.getId(), roomId, new JoinRoomRequest(false, 2, 100L, null));
        rooms.setReady(owner.getId(), roomId, true);
        rooms.setReady(second.getId(), roomId, true);

        assertThat(games.startGame(roomId, owner.getId()).players()).hasSize(2);
    }

    @Test
    void nonHostCannotStart() {
        Fixture fixture = readyFixture();

        assertThatThrownBy(() -> games.startGame(fixture.roomId(), fixture.first().getId()))
                .isInstanceOfSatisfying(GameRuntimeException.class,
                        failure -> assertThat(failure.code()).isEqualTo("ROOM_HOST_REQUIRED"));
        assertThat(sessions.findAllByRoomIdOrderByStartedAtDesc(fixture.roomId())).isEmpty();
    }

    @Test
    void fewerThanTwoPlayersOrAnyUnreadyPlayerBlocksStart() {
        UserEntity owner = user();
        UserEntity first = user();
        UserEntity second = user();
        long roomId = create(owner);
        rooms.join(first.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        rooms.setReady(first.getId(), roomId, true);
        assertCode(roomId, owner.getId(), "INSUFFICIENT_PLAYERS");

        rooms.join(second.getId(), roomId, new JoinRoomRequest(false, 2, 100L, null));
        assertCode(roomId, owner.getId(), "PLAYERS_NOT_READY");
    }

    @Test
    void concurrentDuplicateHostStartsCreateOneSession() throws Exception {
        Fixture fixture = readyFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Throwable>> outcomes = List.of(
                    executor.submit(() -> startTogether(fixture, ready, go)),
                    executor.submit(() -> startTogether(fixture, ready, go)));
            ready.await();
            go.countDown();
            assertThat(outcomes).extracting(this::result).filteredOn(value -> value == null).hasSize(1);
        }
        assertThat(sessions.findAllByRoomIdOrderByStartedAtDesc(fixture.roomId())).hasSize(1);
    }

    @Test
    void aConcurrentDepartureAndStartResolveToOneCoherentRoomState() throws Exception {
        Fixture fixture = readyFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> start = executor.submit(() -> startTogether(fixture, ready, go));
            Future<Throwable> leave = executor.submit(() -> {
                ready.countDown();
                try { go.await(); rooms.leave(fixture.second().getId(), fixture.roomId()); return null; }
                catch (Throwable failure) { return failure; }
            });
            ready.await();
            go.countDown();
            result(start);
            result(leave);
        }
        RoomStatus status = roomRepository.findById(fixture.roomId()).orElseThrow().getStatus();
        assertThat(status).isIn(RoomStatus.WAITING, RoomStatus.PLAYING);
        assertThat(sessions.findAllByRoomIdOrderByStartedAtDesc(fixture.roomId())).hasSizeLessThanOrEqualTo(1);
    }

    private void assertCode(long roomId, long ownerId, String code) {
        assertThatThrownBy(() -> games.startGame(roomId, ownerId))
                .isInstanceOfSatisfying(GameRuntimeException.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private void assertWaitingWithoutSession(long roomId) {
        assertThat(roomRepository.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(sessions.findAllByRoomIdOrderByStartedAtDesc(roomId)).isEmpty();
    }

    private Throwable startTogether(Fixture fixture, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try { go.await(); games.startGame(fixture.roomId(), fixture.owner().getId()); return null; }
        catch (Throwable failure) { return failure; }
    }

    private Throwable result(Future<Throwable> future) {
        try { return future.get(); } catch (Exception failure) { return failure; }
    }

    private Fixture readyFixture() {
        UserEntity owner = user();
        UserEntity first = user();
        UserEntity second = user();
        long roomId = create(owner);
        rooms.join(first.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        rooms.join(second.getId(), roomId, new JoinRoomRequest(false, 2, 100L, null));
        rooms.setReady(first.getId(), roomId, true);
        rooms.setReady(second.getId(), roomId, true);
        return new Fixture(roomId, owner, first, second);
    }

    private long create(UserEntity owner) {
        return rooms.create(owner.getId(), new CreateRoomRequest(
                "host-" + shortId(), RoomType.PUBLIC, 6, 5, 10, 100, null)).room().id();
    }

    private UserEntity user() {
        String suffix = shortId();
        return users.saveAndFlush(new UserEntity("host_" + suffix, passwords.encode("password"),
                "host_" + suffix + "@example.test", Role.PLAYER, AccountStatus.ACTIVE, 1_000));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(long roomId, UserEntity owner, UserEntity first, UserEntity second) {}
}
