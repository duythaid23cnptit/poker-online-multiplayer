package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.game.application.AcceptedActionHistory;
import com.ptit.poker.game.application.HandCompletionReason;
import com.ptit.poker.game.application.HandHistoryHandle;
import com.ptit.poker.game.application.HandHistoryPersistenceService;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
class GameplayHistoryConcurrencyMySqlIntegrationTests {
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired GameSessionRepository sessions;
    @Autowired PokerHandRepository hands;
    @Autowired PlayerActionRepository actions;
    @Autowired HandHistoryPersistenceService history;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> handIds = new ArrayList<>();
    private final List<Long> sessionIds = new ArrayList<>();
    private final List<Long> roomIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanOwnedFixtures() {
        handIds.forEach(id -> jdbc.update("DELETE FROM player_actions WHERE poker_hand_id = ?", id));
        handIds.forEach(id -> jdbc.update("DELETE FROM poker_hands WHERE id = ?", id));
        sessionIds.forEach(id -> jdbc.update("DELETE FROM game_sessions WHERE id = ?", id));
        roomIds.forEach(id -> jdbc.update("DELETE FROM rooms WHERE id = ?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void twoConcurrentWritersPersistContiguousSequences() throws Exception {
        Fixture fixture = fixture();

        runConcurrentWriters(fixture, 2);

        assertSequences(fixture.handId(), 1L, 2L);
    }

    @Test
    void fourConcurrentWritersPersistOneDistinctContiguousSequenceEach() throws Exception {
        Fixture fixture = fixture();

        runConcurrentWriters(fixture, 4);

        assertSequences(fixture.handId(), 1L, 2L, 3L, 4L);
    }

    @Test
    void failedInsertDoesNotConsumeSequenceOrLeaveGhostAction() {
        Fixture fixture = fixture();
        history.recordAcceptedAction(fixture.handId(), action(fixture.userId()));

        assertThatThrownBy(() -> history.recordAcceptedAction(fixture.handId(), action(Long.MAX_VALUE)))
                .isInstanceOf(DataIntegrityViolationException.class);
        history.recordAcceptedAction(fixture.handId(), action(fixture.userId()));

        assertSequences(fixture.handId(), 1L, 2L);
    }

    @Test
    void childConstraintFailureRollsBackEntireHandCompletion() {
        Fixture fixture = fixture();
        PokerPlayer player = new PokerPlayer(fixture.userId(), 1, 1_000, 0, 0,
                PokerPlayerState.ACTIVE, List.of());
        GameState state = new GameState(UUID.randomUUID(), UUID.randomUUID(), GamePhase.FINISHED,
                1, 1, 1, null, 0, 20, List.of(), List.of(player), Duration.ZERO, 0, null);
        state.completeFinancialSettlement();
        HandSettlementResult settlement = new HandSettlementResult(Map.of(), List.of(), List.of(), List.of(),
                Map.of(fixture.userId(), 0L), Map.of(), 0, true, 1_000, 1_000);
        HandHistoryHandle invalid = new HandHistoryHandle(fixture.handId(), Map.of(fixture.userId(), -1L));

        assertThatThrownBy(() -> history.completeHand(
                invalid, state, settlement, HandCompletionReason.ALL_OTHERS_FOLDED))
                .isInstanceOfAny(DataIntegrityViolationException.class, JpaSystemException.class);

        assertThat(hands.findById(fixture.handId()).orElseThrow().getEndReason()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hand_players WHERE poker_hand_id = ?",
                Long.class, fixture.handId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pots WHERE poker_hand_id = ?",
                Long.class, fixture.handId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uncalled_bet_returns WHERE poker_hand_id = ?",
                Long.class, fixture.handId())).isZero();
    }

    private void runConcurrentWriters(Fixture fixture, int writerCount) throws Exception {
        CountDownLatch ready = new CountDownLatch(writerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < writerCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent writers did not start together");
                    }
                    history.recordAcceptedAction(fixture.handId(), action(fixture.userId()));
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertSequences(long handId, Long... expected) {
        List<PlayerActionEntity> persisted = actions.findAllByPokerHandIdOrderByActionSequence(handId);
        assertThat(persisted).hasSize(expected.length);
        assertThat(persisted).extracting(PlayerActionEntity::getActionSequence).containsExactly(expected);
        assertThat(persisted.stream().map(PlayerActionEntity::getActionSequence).distinct().count())
                .isEqualTo(expected.length);
    }

    private Fixture fixture() {
        String suffix = shortSuffix();
        UserEntity user = users.saveAndFlush(new UserEntity("cu-" + suffix, "test-hash",
                "ce-" + suffix + "@example.test", Role.PLAYER, AccountStatus.ACTIVE, 0));
        userIds.add(user.getId());
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(unique("concurrency-room"), user.getId(),
                RoomType.PUBLIC, null, 6, 10, 20, 1_000, RoomStatus.WAITING, Instant.now()));
        roomIds.add(room.getId());
        GameSessionEntity session = sessions.saveAndFlush(new GameSessionEntity(
                room.getId(), GameSessionStatus.ACTIVE, Instant.now(), null));
        sessionIds.add(session.getId());
        PokerHandEntity hand = hands.saveAndFlush(new PokerHandEntity(session.getId(), 1, 1, 1, 1,
                10, 20, Instant.now(), null, GamePhase.PRE_FLOP, "", null));
        handIds.add(hand.getId());
        return new Fixture(user.getId(), hand.getId());
    }

    private static AcceptedActionHistory action(long userId) {
        return new AcceptedActionHistory(userId, GamePhase.PRE_FLOP, PokerActionType.CHECK,
                0, 0, 0, 1_000, UUID.randomUUID(), UUID.randomUUID());
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(long userId, long handId) {}
}
