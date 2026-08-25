package com.ptit.poker.game.application.runtime;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.application.CanonicalCardCodec;
import com.ptit.poker.game.infrastructure.persistence.*;
import com.ptit.poker.room.domain.*;
import com.ptit.poker.room.infrastructure.persistence.*;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ptit.poker.game.domain.card.Deck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
@Transactional
class GameRuntimeMySqlIntegrationTests {
    @Autowired GameRuntimeService runtime;
    @Autowired ActiveGameRegistry registry;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired RoomPlayerRepository roomPlayers;
    @Autowired GameSessionRepository sessions;
    @Autowired PokerHandRepository hands;
    @Autowired PlayerActionRepository actions;
    @Autowired HandPlayerRepository handPlayers;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DeckFactory deckFactory;

    @BeforeEach
    void deterministicDeck() {
        when(deckFactory.create()).thenAnswer(invocation -> new Deck(new Random(42)));
    }

    @Test
    void startEarlyFoldSynchronizationAndNextHandPersistThroughRealBoundaries() {
        UserEntity first = users.saveAndFlush(user()); UserEntity second = users.saveAndFlush(user());
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(unique("runtime-room"), first.getId(),
                RoomType.PUBLIC, null, 6, 50, 100, 1_000, RoomStatus.WAITING, Instant.now()));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), first.getId(), 1, RoomPlayerState.READY, 1_000));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), second.getId(), 4, RoomPlayerState.READY, 1_000));
        long firstAccount = accountChips(first.getId()); long secondAccount = accountChips(second.getId());

        GameRuntimeView started = runtime.startGame(room.getId());

        assertThat(sessions.findById(started.gameSessionId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.ACTIVE);
        PokerHandEntity firstHand = hands.findAllByGameSessionIdOrderByHandNumber(started.gameSessionId()).getFirst();
        assertThat(firstHand.getHandNumber()).isEqualTo(1);
        assertThat(started.dealerSeat()).isEqualTo(1);
        assertThat(started.smallBlindSeat()).isEqualTo(1);
        assertThat(started.bigBlindSeat()).isEqualTo(4);
        assertThat(roomPlayers.findAllByRoomIdAndLeftAtIsNullOrderById(room.getId()))
                .extracting(RoomPlayerEntity::getPlayerState).containsOnly(RoomPlayerState.PLAYING);

        GameRuntimeView completed = runtime.applyAction(started.gameId(), started.currentTurnUserId(),
                new GameActionIntent(started.turnId(), UUID.randomUUID(), PokerActionType.FOLD, 0));

        assertThat(completed.handCompleted()).isTrue();
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(firstHand.getId()))
                .extracting(PlayerActionEntity::getActionSequence).containsExactly(1L);
        assertThat(hands.findById(firstHand.getId()).orElseThrow().getEndReason())
                .isEqualTo(HandEndReason.ALL_OTHERS_FOLDED);
        assertThat(handPlayers.findAllByPokerHandIdOrderBySeatNumber(firstHand.getId())).hasSize(2);
        assertThat(roomPlayers.findAllByRoomIdAndLeftAtIsNullOrderById(room.getId()))
                .extracting(RoomPlayerEntity::getTableChips)
                .containsExactlyElementsOf(completed.players().stream().map(GameRuntimeView.PlayerView::tableChips).toList());
        assertThat(accountChips(first.getId())).isEqualTo(firstAccount);
        assertThat(accountChips(second.getId())).isEqualTo(secondAccount);

        GameRuntimeView next = runtime.startNextHand(started.gameId());
        assertThat(next.handNumber()).isEqualTo(2);
        assertThat(next.dealerSeat()).isEqualTo(4);
        assertThat(hands.findAllByGameSessionIdOrderByHandNumber(started.gameSessionId()))
                .extracting(PokerHandEntity::getHandNumber).containsExactly(1L, 2L);
    }

    @Test
    void runtimeDrivenShowdownPersistsBoardPotsAwardsCardsAndCommitments() {
        UserEntity first = users.saveAndFlush(user()); UserEntity second = users.saveAndFlush(user());
        long firstAccount = accountChips(first.getId()); long secondAccount = accountChips(second.getId());
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(unique("showdown-room"), first.getId(),
                RoomType.PUBLIC, null, 6, 50, 100, 1_000, RoomStatus.WAITING, Instant.now()));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), first.getId(), 1, RoomPlayerState.READY, 1_000));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), second.getId(), 2, RoomPlayerState.READY, 1_000));

        // Reconstruct the clockwise two-round deal without coupling the assertion to entity ordering.
        Deck expectedDeck = new Deck(new Random(42)); expectedDeck.shuffle();
        var secondFirst = expectedDeck.draw(); var firstFirst = expectedDeck.draw();
        var secondSecond = expectedDeck.draw(); var firstSecond = expectedDeck.draw();
        Map<Long, String> expectedHoleCards = Map.of(second.getId(), CanonicalCardCodec.serialize(List.of(secondFirst, secondSecond)),
                first.getId(), CanonicalCardCodec.serialize(List.of(firstFirst, firstSecond)));
        String expectedBoard = CanonicalCardCodec.serialize(expectedDeck.draw(5));

        GameRuntimeView view = runtime.startGame(room.getId());
        for (int actionsTaken = 0; !view.handCompleted() && actionsTaken < 20; actionsTaken++) {
            long actorId = view.currentTurnUserId();
            GameRuntimeView.PlayerView actor = view.players().stream()
                    .filter(player -> player.userId() == actorId).findFirst().orElseThrow();
            PokerActionType type = actor.currentBet() < view.currentBet() ? PokerActionType.CALL : PokerActionType.CHECK;
            view = runtime.applyAction(view.gameId(), actor.userId(),
                    new GameActionIntent(view.turnId(), UUID.randomUUID(), type, 0));
        }

        assertThat(view.handCompleted()).isTrue();
        PokerHandEntity hand = hands.findAllByGameSessionIdOrderByHandNumber(view.gameSessionId()).getFirst();
        assertThat(hand.getEndReason()).isEqualTo(HandEndReason.SHOWDOWN);
        assertThat(hand.getBoardCards()).isEqualTo(expectedBoard);
        assertThat(hand.getBoardCards().split(",")).hasSize(5).doesNotHaveDuplicates();
        List<HandPlayerEntity> snapshots = handPlayers.findAllByPokerHandIdOrderBySeatNumber(hand.getId());
        assertThat(snapshots).hasSize(2).allSatisfy(snapshot ->
                assertThat(snapshot.getHoleCards()).isEqualTo(expectedHoleCards.get(snapshot.getUserId())));
        for (GameRuntimeView.PlayerView player : view.players()) {
            Map<String, Object> persisted = jdbc.queryForMap("SELECT starting_table_chips, ending_table_chips, "
                    + "total_committed FROM hand_players WHERE poker_hand_id = ? AND user_id = ?",
                    hand.getId(), player.userId());
            assertThat(((Number) persisted.get("starting_table_chips")).longValue()).isEqualTo(1_000);
            assertThat(((Number) persisted.get("ending_table_chips")).longValue()).isEqualTo(player.tableChips());
            assertThat(((Number) persisted.get("total_committed")).longValue()).isEqualTo(100);
        }
        assertThat(view.players()).allSatisfy(player -> assertThat(player.totalCommitted()).isZero());
        assertThat(jdbc.queryForList("SELECT amount FROM pots WHERE poker_hand_id = ? ORDER BY pot_index",
                Long.class, hand.getId())).containsExactly(200L);
        assertThat(jdbc.queryForList("SELECT amount_awarded FROM pot_awards pa JOIN pots p ON p.id=pa.pot_id "
                + "WHERE p.poker_hand_id = ? ORDER BY pa.id", Long.class, hand.getId())).containsExactly(200L);
        assertThat(jdbc.queryForList("SELECT odd_chip_amount FROM pot_awards pa JOIN pots p ON p.id=pa.pot_id "
                + "WHERE p.poker_hand_id = ?", Long.class, hand.getId())).containsOnly(0L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uncalled_bet_returns WHERE poker_hand_id = ?",
                Long.class, hand.getId())).isZero();
        assertRoomStacks(room.getId(), view);
        assertThat(accountChips(first.getId())).isEqualTo(firstAccount);
        assertThat(accountChips(second.getId())).isEqualTo(secondAccount);
    }

    @Test
    void automaticRunoutBustsPlayerAndFinishesSessionWithoutSecondHand() {
        UserEntity first = users.saveAndFlush(user()); UserEntity second = users.saveAndFlush(user());
        long firstAccount = accountChips(first.getId()); long secondAccount = accountChips(second.getId());
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(unique("finish-room"), first.getId(),
                RoomType.PUBLIC, null, 6, 50, 100, 100, RoomStatus.WAITING, Instant.now()));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), first.getId(), 1, RoomPlayerState.READY, 50));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), second.getId(), 2, RoomPlayerState.READY, 100));

        GameRuntimeView completed = runtime.startGame(room.getId());
        assertThat(completed.handCompleted()).isTrue();
        assertThat(completed.players()).filteredOn(player -> player.tableChips() == 0).hasSize(1);
        assertThat(roomPlayers.findAllByRoomIdAndLeftAtIsNullOrderById(room.getId()))
                .extracting(RoomPlayerEntity::getTableChips)
                .containsExactlyElementsOf(completed.players().stream().map(GameRuntimeView.PlayerView::tableChips).toList());

        GameRuntimeView finished = runtime.startNextHand(completed.gameId());

        assertThat(finished.sessionFinished()).isTrue();
        assertThat(sessions.findById(finished.gameSessionId()).orElseThrow().getStatus())
                .isEqualTo(GameSessionStatus.FINISHED);
        assertThat(hands.findAllByGameSessionIdOrderByHandNumber(finished.gameSessionId())).hasSize(1);
        assertThatThrownBy(() -> registry.require(completed.gameId()))
                .isInstanceOf(GameRuntimeException.class).hasMessageContaining("GAME_NOT_ACTIVE");
        assertRoomStacks(room.getId(), completed);
        assertThat(accountChips(first.getId())).isEqualTo(firstAccount);
        assertThat(accountChips(second.getId())).isEqualTo(secondAccount);
    }

    @Test
    void threePlayerAllInExcludesBustedPlayerFromPersistedSecondHand() {
        when(deckFactory.create()).thenAnswer(invocation -> new Deck(new Random(3)));
        UserEntity first = users.saveAndFlush(user()); UserEntity second = users.saveAndFlush(user());
        UserEntity third = users.saveAndFlush(user());
        long[] accounts = {accountChips(first.getId()), accountChips(second.getId()), accountChips(third.getId())};
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(unique("bust-room"), first.getId(),
                RoomType.PUBLIC, null, 6, 50, 100, 1_000, RoomStatus.WAITING, Instant.now()));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), first.getId(), 1, RoomPlayerState.READY, 50));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), second.getId(), 2, RoomPlayerState.READY, 100));
        roomPlayers.saveAndFlush(new RoomPlayerEntity(room.getId(), third.getId(), 3, RoomPlayerState.READY, 1_000));

        GameRuntimeView view = runtime.startGame(room.getId());
        assertThat(view.phase()).isEqualTo(com.ptit.poker.game.domain.state.GamePhase.PRE_FLOP);
        assertThat(view.currentTurnUserId()).isEqualTo(first.getId());
        view = runtime.applyAction(view.gameId(), first.getId(),
                new GameActionIntent(view.turnId(), UUID.randomUUID(), PokerActionType.ALL_IN, 0));
        assertThat(view.currentTurnUserId()).isEqualTo(second.getId());
        view = runtime.applyAction(view.gameId(), second.getId(),
                new GameActionIntent(view.turnId(), UUID.randomUUID(), PokerActionType.ALL_IN, 0));
        assertThat(view.currentTurnUserId()).isEqualTo(third.getId());
        view = runtime.applyAction(view.gameId(), third.getId(),
                new GameActionIntent(view.turnId(), UUID.randomUUID(), PokerActionType.CHECK, 0));
        assertThat(view.handCompleted()).isTrue();
        List<Long> busted = view.players().stream().filter(player -> player.tableChips() == 0)
                .map(GameRuntimeView.PlayerView::userId).toList();
        assertThat(busted).isNotEmpty();
        PokerHandEntity handOne = hands.findAllByGameSessionIdOrderByHandNumber(view.gameSessionId()).getFirst();
        assertThat(handOne.getEndReason()).isEqualTo(HandEndReason.SHOWDOWN);
        assertThat(handPlayers.findAllByPokerHandIdOrderBySeatNumber(handOne.getId()))
                .extracting(HandPlayerEntity::getUserId).containsAll(busted);

        GameRuntimeView next = runtime.startNextHand(view.gameId());
        assertThat(next.handNumber()).isEqualTo(2);
        assertThat(next.players()).extracting(GameRuntimeView.PlayerView::userId).doesNotContainAnyElementsOf(busted);
        assertThat(next.players()).allSatisfy(player -> assertThat(player.holeCardCount()).isEqualTo(2));
        assertThat(List.of(next.dealerSeat(), next.smallBlindSeat(), next.bigBlindSeat()))
                .doesNotContainAnyElementsOf(view.players().stream().filter(player -> busted.contains(player.userId()))
                        .map(GameRuntimeView.PlayerView::seat).toList());
        assertThat(roomPlayers.findAllByRoomIdAndLeftAtIsNullOrderById(room.getId()).stream()
                .filter(player -> busted.contains(player.getUserId())))
                .allMatch(player -> player.getTableChips() == 0);
        assertRoomStacks(room.getId(), view);
        List<PokerHandEntity> persistedHands = hands.findAllByGameSessionIdOrderByHandNumber(view.gameSessionId());
        assertThat(persistedHands).extracting(PokerHandEntity::getHandNumber).containsExactly(1L, 2L);
        PokerHandEntity handTwo = persistedHands.get(1);
        assertThat(handTwo.getEndReason()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hand_players WHERE poker_hand_id = ? "
                + "AND user_id IN (" + busted.stream().map(ignored -> "?").collect(java.util.stream.Collectors.joining(",")) + ")",
                Long.class, java.util.stream.Stream.concat(java.util.stream.Stream.of(handTwo.getId()), busted.stream()).toArray()))
                .isZero();
        assertThat(accountChips(first.getId())).isEqualTo(accounts[0]);
        assertThat(accountChips(second.getId())).isEqualTo(accounts[1]);
        assertThat(accountChips(third.getId())).isEqualTo(accounts[2]);
    }

    private long accountChips(long userId) {
        return jdbc.queryForObject("SELECT account_chips FROM users WHERE id = ?", Long.class, userId);
    }
    private void assertRoomStacks(long roomId, GameRuntimeView view) {
        Map<Long, Long> authoritative = view.players().stream().collect(java.util.stream.Collectors.toMap(
                GameRuntimeView.PlayerView::userId, GameRuntimeView.PlayerView::tableChips));
        assertThat(roomPlayers.findAllByRoomIdAndLeftAtIsNullOrderById(roomId)).allSatisfy(player ->
                assertThat(player.getTableChips()).isEqualTo(authoritative.get(player.getUserId())));
    }
    private static UserEntity user() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new UserEntity("gr-" + suffix, "test-hash", "gr-" + suffix + "@example.test",
                Role.PLAYER, AccountStatus.ACTIVE, 5_000);
    }
    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
}
