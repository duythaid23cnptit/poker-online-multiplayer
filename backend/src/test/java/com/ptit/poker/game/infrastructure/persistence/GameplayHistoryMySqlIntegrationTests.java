package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.pot.PotType;
import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
@Transactional
class GameplayHistoryMySqlIntegrationTests {

    private static final Set<String> TABLES = Set.of(
            "game_sessions", "poker_hands", "hand_players", "player_actions",
            "pots", "pot_awards", "uncalled_bet_returns");

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired GameSessionRepository sessions;
    @Autowired PokerHandRepository hands;
    @Autowired HandPlayerRepository handPlayers;
    @Autowired PlayerActionRepository actions;
    @Autowired PotRepository pots;
    @Autowired PotAwardRepository awards;
    @Autowired UncalledBetReturnRepository returns;

    @Test
    void flywayV5AndHibernateValidateAllGameplayHistoryTables() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
        Set<String> actual = Set.copyOf(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class));
        assertThat(actual).containsAll(TABLES);
    }

    @Test
    void sessionOwnsOrderedHandsAndSequenceIsUniqueOnlyWithinSession() {
        Fixture fixture = fixture();
        GameSessionEntity firstSession = fixture.session();
        GameSessionEntity secondSession = sessions.saveAndFlush(
                new GameSessionEntity(fixture.room().getId(), GameSessionStatus.ACTIVE, Instant.now(), null));
        PokerHandEntity first = hands.saveAndFlush(hand(firstSession.getId(), 1));
        PokerHandEntity second = hands.saveAndFlush(hand(firstSession.getId(), 2));
        PokerHandEntity otherSessionSameNumber = hands.saveAndFlush(hand(secondSession.getId(), 1));

        assertThat(hands.findAllByGameSessionIdOrderByHandNumber(firstSession.getId()))
                .extracting(PokerHandEntity::getId).containsExactly(first.getId(), second.getId());
        assertThat(otherSessionSameNumber.getId()).isNotNull();
        assertThatThrownBy(() -> hands.saveAndFlush(hand(firstSession.getId(), 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void handPlayerPreservesOrthogonalHistoryAndRejectsDuplicateUser() {
        Fixture fixture = fixtureWithHand();
        HandPlayerEntity allInDisconnected = handPlayers.saveAndFlush(new HandPlayerEntity(
                fixture.hand().getId(), fixture.user().getId(), 1, 1_000, 1_400, 500,
                PokerPlayerState.ALL_IN, false, false, "AS,KD"));

        assertThat(allInDisconnected.getParticipationState()).isEqualTo(PokerPlayerState.ALL_IN);
        assertThat(allInDisconnected.isConnectedAtEnd()).isFalse();
        assertThat(allInDisconnected.getHoleCards()).isEqualTo("AS,KD");
        assertThatThrownBy(() -> handPlayers.saveAndFlush(new HandPlayerEntity(
                fixture.hand().getId(), fixture.user().getId(), 2, 1_000, 900, 100,
                PokerPlayerState.FOLDED, true, false, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void handPlayerRejectsDuplicateSeatInvalidSeatAndNegativeChips() {
        Fixture fixture = fixtureWithHand();
        UserEntity second = users.saveAndFlush(user());
        handPlayers.saveAndFlush(new HandPlayerEntity(
                fixture.hand().getId(), fixture.user().getId(), 1, 1_000, 900, 100,
                PokerPlayerState.FOLDED, true, false, null));

        assertPersistenceFailure(() -> handPlayers.saveAndFlush(new HandPlayerEntity(
                fixture.hand().getId(), second.getId(), 1, 1_000, 900, 100,
                PokerPlayerState.ACTIVE, true, false, "2C,3D")));
    }

    @Test
    void handPlayerRejectsInvalidSeat() {
        Fixture fixture = fixtureWithHand();
        assertPersistenceFailure(() -> handPlayers.saveAndFlush(new HandPlayerEntity(
                fixture.hand().getId(), fixture.user().getId(), 10, 1_000, 900, 100,
                PokerPlayerState.FOLDED, true, false, null)));
    }

    @Test
    void handPlayerRejectsNegativeChipSnapshot() {
        Fixture fixture = fixtureWithHand();
        assertPersistenceFailure(() -> handPlayers.saveAndFlush(new HandPlayerEntity(
                fixture.hand().getId(), fixture.user().getId(), 1, -1, 900, 100,
                PokerPlayerState.FOLDED, true, false, null)));
    }

    @Test
    void acceptedActionsPersistAllTypesInExplicitSequenceAndRejectDuplicates() {
        Fixture fixture = fixtureWithHand();
        long sequence = 1;
        for (PokerActionType type : PokerActionType.values()) {
            actions.saveAndFlush(new PlayerActionEntity(
                    fixture.hand().getId(), fixture.user().getId(), sequence++, GamePhase.PRE_FLOP,
                    type, 0, 0, 20, 1_000, UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        }

        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(fixture.hand().getId()))
                .extracting(PlayerActionEntity::getActionType)
                .containsExactly(PokerActionType.values());
        assertThatThrownBy(() -> actions.saveAndFlush(new PlayerActionEntity(
                fixture.hand().getId(), fixture.user().getId(), 1, GamePhase.FLOP,
                PokerActionType.CHECK, 0, 0, 0, 1_000, null, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderedPotsSplitAwardsAndUncalledReturnArePersistedExplicitly() {
        Fixture fixture = fixtureWithHand();
        UserEntity second = users.saveAndFlush(user());
        PotEntity main = pots.saveAndFlush(new PotEntity(fixture.hand().getId(), 0, PotType.MAIN, 301, 100));
        PotEntity side = pots.saveAndFlush(new PotEntity(fixture.hand().getId(), 1, PotType.SIDE, 200, 200));
        awards.saveAndFlush(new PotAwardEntity(main.getId(), fixture.user().getId(), 151, 1));
        awards.saveAndFlush(new PotAwardEntity(main.getId(), second.getId(), 150, 0));
        returns.saveAndFlush(new UncalledBetReturnEntity(fixture.hand().getId(), second.getId(), 50));

        assertThat(pots.findAllByPokerHandIdOrderByPotIndex(fixture.hand().getId()))
                .extracting(PotEntity::getPotType).containsExactly(PotType.MAIN, PotType.SIDE);
        assertThat(awards.findAllByPotIdOrderById(main.getId())).hasSize(2);
        assertThat(awards.findAllByPotIdOrderById(main.getId()))
                .extracting(PotAwardEntity::getAmountAwarded).containsExactly(151L, 150L);
        assertThat(returns.findAllByPokerHandIdOrderById(fixture.hand().getId()))
                .extracting(UncalledBetReturnEntity::getAmount).containsExactly(50L);
    }

    @Test
    void databaseRejectsOrphanHistoryAndInvalidFinancialValues() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO poker_hands (game_session_id, hand_number, dealer_seat, small_blind_seat, "
                        + "big_blind_seat, small_blind_amount, big_blind_amount, started_at, final_phase, board_cards) "
                        + "VALUES (?, 1, 1, 2, 3, 10, 20, UTC_TIMESTAMP(6), 'FINISHED', '')",
                Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);

        Fixture fixture = fixtureWithHand();
        assertPersistenceFailure(() -> pots.saveAndFlush(
                new PotEntity(fixture.hand().getId(), 0, PotType.MAIN, -1, 100)));
    }

    private Fixture fixture() {
        UserEntity owner = users.saveAndFlush(user());
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(
                unique("room"), owner.getId(), RoomType.PUBLIC, null, 6,
                10, 20, 1_000, RoomStatus.WAITING, Instant.now()));
        GameSessionEntity session = sessions.saveAndFlush(
                new GameSessionEntity(room.getId(), GameSessionStatus.ACTIVE, Instant.now(), null));
        return new Fixture(owner, room, session, null);
    }

    private Fixture fixtureWithHand() {
        Fixture base = fixture();
        PokerHandEntity hand = hands.saveAndFlush(hand(base.session().getId(), 1));
        return new Fixture(base.user(), base.room(), base.session(), hand);
    }

    private static PokerHandEntity hand(Long sessionId, long number) {
        Instant now = Instant.now();
        return new PokerHandEntity(sessionId, number, 1, 2, 3, 10, 20,
                now, now, GamePhase.FINISHED, "AS,KD,7H,6C,2S", HandEndReason.SHOWDOWN);
    }

    private static UserEntity user() {
        return new UserEntity(unique("user"), "test-hash", unique("email") + "@example.test",
                Role.PLAYER, AccountStatus.ACTIVE, 0);
    }

    private static void assertPersistenceFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfAny(DataIntegrityViolationException.class, JpaSystemException.class);
    }

    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }

    private record Fixture(UserEntity user, RoomEntity room, GameSessionEntity session, PokerHandEntity hand) {}
}
