package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.application.AcceptedActionHistory;
import com.ptit.poker.game.application.AcceptedActionRecord;
import com.ptit.poker.game.application.CanonicalCardCodec;
import com.ptit.poker.game.application.GameSessionPersistenceService;
import com.ptit.poker.game.application.GameplayHistoryException;
import com.ptit.poker.game.application.HandCompletionReason;
import com.ptit.poker.game.application.HandHistoryHandle;
import com.ptit.poker.game.application.HandHistoryPersistenceService;
import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Rank;
import com.ptit.poker.game.domain.card.Suit;
import com.ptit.poker.game.domain.hand.HandEvaluator;
import com.ptit.poker.game.domain.settlement.HandSettlementEngine;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayer;
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
import java.time.Duration;
import java.util.List;
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
    @Autowired GameSessionPersistenceService sessionHistory;
    @Autowired HandHistoryPersistenceService handHistory;

    @Test
    void applicationServiceOwnsSessionLifecycleAndRejectsRepeatedCompletion() {
        UserEntity owner = users.saveAndFlush(user());
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(
                unique("room"), owner.getId(), RoomType.PUBLIC, null, 6,
                10, 20, 1_000, RoomStatus.WAITING, Instant.now()));

        var started = sessionHistory.startSession(room.getId());
        var finished = sessionHistory.finishSession(started.id());

        assertThat(started.status()).isEqualTo(GameSessionStatus.ACTIVE);
        assertThat(finished.status()).isEqualTo(GameSessionStatus.FINISHED);
        assertThat(finished.endedAt()).isNotNull();
        assertThatThrownBy(() -> sessionHistory.abortSession(started.id()))
                .isInstanceOfSatisfying(GameplayHistoryException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_SESSION_TRANSITION"));
    }

    @Test
    void applicationServicePersistsHandsAndAllAcceptedActionsInDeterministicOrder() {
        Fixture fixture = fixture();
        GameState state = singlePlayerPreFlopState(fixture.user().getId(), 1_000);
        HandHistoryHandle first = handHistory.startHand(fixture.session().getId(), 1, 10, 20, state);
        HandHistoryHandle second = handHistory.startHand(fixture.session().getId(), 2, 10, 20, state);

        long amount = 0;
        AcceptedActionRecord lastRecord = null;
        for (PokerActionType type : PokerActionType.values()) {
            lastRecord = handHistory.recordAcceptedAction(first.pokerHandId(), new AcceptedActionHistory(
                    fixture.user().getId(), GamePhase.PRE_FLOP, type, amount++, 20, 20,
                    980, UUID.randomUUID(), UUID.randomUUID()));
        }

        assertThat(hands.findAllByGameSessionIdOrderByHandNumber(fixture.session().getId()))
                .extracting(PokerHandEntity::getId).containsExactly(first.pokerHandId(), second.pokerHandId());
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(first.pokerHandId()))
                .extracting(PlayerActionEntity::getActionSequence).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(lastRecord).isNotNull();
        assertThat(lastRecord.id()).isPositive();
        assertThat(lastRecord.actionSequence()).isEqualTo(6);
    }

    @Test
    void completedShowdownPersistsAuthoritativeCardsCommitmentsAndAwardsOnce() {
        UserEntity firstUser = users.saveAndFlush(user());
        UserEntity secondUser = users.saveAndFlush(user());
        UserEntity thirdUser = users.saveAndFlush(user());
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(
                unique("room"), firstUser.getId(), RoomType.PUBLIC, null, 6,
                10, 20, 1_000, RoomStatus.WAITING, Instant.now()));
        long sessionId = sessionHistory.startSession(room.getId()).id();
        HandHistoryHandle handle = handHistory.startHand(sessionId, 1, 10, 20,
                threePlayerPreFlopState(firstUser.getId(), secondUser.getId(), thirdUser.getId()));

        PokerPlayer first = player(firstUser.getId(), 1, 900, 100, "AH", "AD");
        PokerPlayer second = player(secondUser.getId(), 2, 700, 300, "KH", "KD");
        PokerPlayer third = player(thirdUser.getId(), 3, 500, 500, "QH", "QD");
        GameState showdown = state(GamePhase.SHOWDOWN, List.of(first, second, third),
                List.of(card("JC"), card("9S"), card("7H"), card("3D"), card("2C")));
        HandSettlementResult result = new HandSettlementEngine(new HandEvaluator()).settleShowdown(showdown);

        assertThat(result.totalCommittedByUser()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                firstUser.getId(), 100L, secondUser.getId(), 300L, thirdUser.getId(), 500L));
        assertThat(CanonicalCardCodec.serialize(showdown.communityCards()))
                .isEqualTo("JC,9S,7H,3D,2C");
        assertThat(showdown.players()).allSatisfy(player -> assertThat(player.totalCommitted()).isZero());
        assertThatThrownBy(() -> result.totalCommittedByUser().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        handHistory.completeHand(handle, showdown, result, HandCompletionReason.SHOWDOWN);

        PokerHandEntity persisted = hands.findById(handle.pokerHandId()).orElseThrow();
        assertThat(persisted.getBoardCards()).isEqualTo("JC,9S,7H,3D,2C");
        assertThat(jdbc.queryForList(
                "SELECT user_id, starting_table_chips, ending_table_chips, total_committed "
                        + "FROM hand_players WHERE poker_hand_id = ? ORDER BY seat_number",
                handle.pokerHandId()))
                .extracting(row -> List.of(((Number) row.get("user_id")).longValue(),
                        ((Number) row.get("starting_table_chips")).longValue(),
                        ((Number) row.get("ending_table_chips")).longValue(),
                        ((Number) row.get("total_committed")).longValue()))
                .containsExactly(
                        List.of(firstUser.getId(), 1_000L, first.tableChips(), 100L),
                        List.of(secondUser.getId(), 1_000L, second.tableChips(), 300L),
                        List.of(thirdUser.getId(), 1_000L, third.tableChips(), 500L));
        assertThat(pots.findAllByPokerHandIdOrderByPotIndex(handle.pokerHandId())).hasSize(2);
        assertThat(returns.findAllByPokerHandIdOrderById(handle.pokerHandId())).hasSize(1);
        long awardCount = jdbc.queryForObject("SELECT COUNT(*) FROM pot_awards pa JOIN pots p ON p.id = pa.pot_id "
                + "WHERE p.poker_hand_id = ?", Long.class, handle.pokerHandId());
        assertThatThrownBy(() -> handHistory.completeHand(handle, showdown, result, HandCompletionReason.SHOWDOWN))
                .isInstanceOfSatisfying(GameplayHistoryException.class,
                        error -> assertThat(error.code()).isEqualTo("POKER_HAND_ALREADY_COMPLETED"));
        assertThat(handPlayers.findAllByPokerHandIdOrderBySeatNumber(handle.pokerHandId())).hasSize(3);
        assertThat(pots.findAllByPokerHandIdOrderByPotIndex(handle.pokerHandId())).hasSize(2);
        assertThat(returns.findAllByPokerHandIdOrderById(handle.pokerHandId())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pot_awards pa JOIN pots p ON p.id = pa.pot_id "
                + "WHERE p.poker_hand_id = ?", Long.class, handle.pokerHandId())).isEqualTo(awardCount);
        assertIncompleteSnapshotRejectedBeforePersistence();
    }

    private void assertIncompleteSnapshotRejectedBeforePersistence() {
        UserEntity firstUser = users.saveAndFlush(user());
        UserEntity secondUser = users.saveAndFlush(user());
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(
                unique("room"), firstUser.getId(), RoomType.PUBLIC, null, 6,
                10, 20, 1_000, RoomStatus.WAITING, Instant.now()));
        long sessionId = sessionHistory.startSession(room.getId()).id();
        HandHistoryHandle valid = handHistory.startHand(sessionId, 1, 10, 20,
                twoPlayerPreFlopState(firstUser.getId(), secondUser.getId()));
        HandHistoryHandle incomplete = new HandHistoryHandle(valid.pokerHandId(),
                java.util.Map.of(firstUser.getId(), 1_000L));
        GameState showdown = state(GamePhase.SHOWDOWN, List.of(
                        player(firstUser.getId(), 1, "KH", "KD"),
                        player(secondUser.getId(), 2, "AH", "QD")),
                List.of(card("KC"), card("9S"), card("7H"), card("3D"), card("2C")));
        HandSettlementResult result = new HandSettlementEngine(new HandEvaluator()).settleShowdown(showdown);

        assertThatThrownBy(() -> handHistory.completeHand(
                incomplete, showdown, result, HandCompletionReason.SHOWDOWN))
                .isInstanceOfSatisfying(GameplayHistoryException.class,
                        error -> assertThat(error.code()).contains(
                                "HAND_PLAYER_SNAPSHOT_MISSING:userId=" + secondUser.getId()));
        assertThat(hands.findById(valid.pokerHandId()).orElseThrow().getEndReason()).isNull();
        assertThat(handPlayers.findAllByPokerHandIdOrderBySeatNumber(valid.pokerHandId())).isEmpty();
        assertThat(pots.findAllByPokerHandIdOrderByPotIndex(valid.pokerHandId())).isEmpty();
    }

    @Test
    void flywayV7PreservesAndHibernateValidatesAllGameplayHistoryTables() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
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

    private static GameState singlePlayerPreFlopState(long userId, long chips) {
        PokerPlayer player = new PokerPlayer(userId, 1, chips, 0, 0,
                PokerPlayerState.ACTIVE, List.of());
        return new GameState(UUID.randomUUID(), UUID.randomUUID(), GamePhase.PRE_FLOP,
                1, 1, 1, userId, 20, 20, List.of(), List.of(player),
                Duration.ofSeconds(30), 0, UUID.randomUUID());
    }

    private static GameState twoPlayerPreFlopState(long firstUserId, long secondUserId) {
        return new GameState(UUID.randomUUID(), UUID.randomUUID(), GamePhase.PRE_FLOP,
                1, 1, 2, firstUserId, 100, 20, List.of(), List.of(
                new PokerPlayer(firstUserId, 1, 900, 100, 100, PokerPlayerState.ACTIVE, List.of()),
                new PokerPlayer(secondUserId, 2, 900, 100, 100, PokerPlayerState.ACTIVE, List.of())),
                Duration.ofSeconds(30), 0, UUID.randomUUID());
    }

    private static GameState threePlayerPreFlopState(long firstUserId, long secondUserId, long thirdUserId) {
        return new GameState(UUID.randomUUID(), UUID.randomUUID(), GamePhase.PRE_FLOP,
                1, 2, 3, firstUserId, 500, 20, List.of(), List.of(
                new PokerPlayer(firstUserId, 1, 900, 100, 100, PokerPlayerState.ACTIVE, List.of()),
                new PokerPlayer(secondUserId, 2, 700, 300, 300, PokerPlayerState.ACTIVE, List.of()),
                new PokerPlayer(thirdUserId, 3, 500, 500, 500, PokerPlayerState.ACTIVE, List.of())),
                Duration.ofSeconds(30), 0, UUID.randomUUID());
    }

    private static GameState state(GamePhase phase, List<PokerPlayer> players, List<Card> board) {
        long currentBet = players.stream().mapToLong(PokerPlayer::currentBet).max().orElse(0);
        return new GameState(UUID.randomUUID(), UUID.randomUUID(), phase,
                1, 1, 2, null, currentBet, 20, board, players,
                Duration.ZERO, 0, null);
    }

    private static PokerPlayer player(long userId, int seat, String first, String second) {
        return player(userId, seat, 900, 100, first, second);
    }

    private static PokerPlayer player(long userId, int seat, long tableChips, long committed,
                                      String first, String second) {
        return new PokerPlayer(userId, seat, tableChips, committed, committed, PokerPlayerState.ACTIVE,
                List.of(card(first), card(second)));
    }

    private static Card card(String code) {
        Rank rank = switch (code.charAt(0)) {
            case '2' -> Rank.TWO; case '3' -> Rank.THREE; case '7' -> Rank.SEVEN;
            case '9' -> Rank.NINE; case 'J' -> Rank.JACK; case 'Q' -> Rank.QUEEN; case 'K' -> Rank.KING;
            case 'A' -> Rank.ACE; default -> throw new IllegalArgumentException(code);
        };
        Suit suit = switch (code.charAt(1)) {
            case 'C' -> Suit.CLUBS; case 'D' -> Suit.DIAMONDS;
            case 'H' -> Suit.HEARTS; case 'S' -> Suit.SPADES;
            default -> throw new IllegalArgumentException(code);
        };
        return new Card(rank, suit);
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
