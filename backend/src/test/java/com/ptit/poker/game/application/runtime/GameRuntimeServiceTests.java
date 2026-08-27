package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.application.*;
import com.ptit.poker.game.application.AcceptedActionHistory;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.domain.card.Deck;
import com.ptit.poker.game.domain.state.GameState;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import com.ptit.poker.game.infrastructure.persistence.GameSessionStatus;
import com.ptit.poker.room.domain.RoomPlayerState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameRuntimeServiceTests {
    private final RoomGamePort rooms = mock(RoomGamePort.class);
    private final GameSessionPersistenceService sessions = mock(GameSessionPersistenceService.class);
    private final HandHistoryPersistenceService history = mock(HandHistoryPersistenceService.class);
    private GameRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new GameRuntimeService(new ActiveGameRegistry(), rooms, sessions, history,
                () -> new Deck(new Random(42)));
        when(sessions.startSession(anyLong())).thenReturn(new GameSessionView(
                10, 1, GameSessionStatus.ACTIVE, Instant.EPOCH, null));
        when(history.startHand(anyLong(), anyLong(), anyLong(), anyLong(), any(GameState.class)))
                .thenAnswer(invocation -> {
                    GameState state = invocation.getArgument(4);
                    return new HandHistoryHandle(20, state.players().stream().collect(java.util.stream.Collectors.toMap(
                            p -> p.userId(), p -> Math.addExact(p.tableChips(), p.totalCommitted()))));
                });
    }

    @Test
    void threePlayerStartUsesLowestDealerClockwiseBlindsAndPostsExactlyOnce() {
        when(rooms.load(1)).thenReturn(room(seat(1, 1, 1_000), seat(2, 4, 1_000), seat(3, 8, 1_000)));

        GameRuntimeView view = service.startGame(1);

        assertThat(view.dealerSeat()).isEqualTo(1);
        assertThat(view.smallBlindSeat()).isEqualTo(4);
        assertThat(view.bigBlindSeat()).isEqualTo(8);
        assertThat(view.currentTurnUserId()).isEqualTo(1);
        assertThat(view.players()).extracting(GameRuntimeView.PlayerView::tableChips)
                .containsExactly(1_000L, 950L, 900L);
        verify(history).startHand(eq(10L), eq(1L), eq(50L), eq(100L), any(GameState.class));
    }

    @Test
    void headsUpDealerIsSmallBlindAndActsFirstPreFlop() {
        when(rooms.load(1)).thenReturn(room(seat(1, 2, 1_000), seat(2, 7, 1_000)));

        GameRuntimeView view = service.startGame(1);

        assertThat(view.dealerSeat()).isEqualTo(2);
        assertThat(view.smallBlindSeat()).isEqualTo(2);
        assertThat(view.bigBlindSeat()).isEqualTo(7);
        assertThat(view.currentTurnUserId()).isEqualTo(1L);
    }

    @Test
    void shortBigBlindPostsAvailableStackThenAutomaticRunoutSettlesIt() {
        when(rooms.load(1)).thenReturn(room(seat(1, 1, 1_000), seat(2, 2, 60)));

        GameRuntimeView view = service.startGame(1);

        ArgumentCaptor<HandSettlementResult> settlement = ArgumentCaptor.forClass(HandSettlementResult.class);
        verify(history).completeHand(any(), any(), settlement.capture(), eq(HandCompletionReason.SHOWDOWN));
        assertThat(settlement.getValue().totalCommittedByUser()).containsEntry(2L, 60L);
        assertThat(view.handCompleted()).isTrue();
        assertThat(view.currentTurnUserId()).isNull();
    }

    @Test
    void rejectsInsufficientPlayersAndDuplicateRoomStart() {
        when(rooms.load(1)).thenReturn(room(seat(1, 1, 1_000)));
        assertThatThrownBy(() -> service.startGame(1)).isInstanceOf(GameRuntimeException.class);

        when(rooms.load(1)).thenReturn(room(seat(1, 1, 1_000), seat(2, 2, 1_000)));
        service.startGame(1);
        assertThatThrownBy(() -> service.startGame(1))
                .isInstanceOfSatisfying(GameRuntimeException.class,
                        error -> assertThat(error.code()).isEqualTo("GAME_ALREADY_ACTIVE"));
    }

    @Test
    void delegatesAllSixAcceptedActionsAndPersistsEachExactlyOnce() {
        applyFirstAction(11, PokerActionType.FOLD, 0);
        applyFirstAction(12, PokerActionType.CALL, 0);
        applyFirstAction(13, PokerActionType.RAISE, 200);
        applyFirstAction(14, PokerActionType.ALL_IN, 0);

        GameRuntimeView checkGame = startHeadsUp(15);
        checkGame = service.applyAction(checkGame.gameId(), 1,
                intent(checkGame, PokerActionType.CALL, 0));
        service.applyAction(checkGame.gameId(), 2, intent(checkGame, PokerActionType.CHECK, 0));

        GameRuntimeView betGame = startHeadsUp(16);
        betGame = service.applyAction(betGame.gameId(), 1, intent(betGame, PokerActionType.CALL, 0));
        betGame = service.applyAction(betGame.gameId(), 2, intent(betGame, PokerActionType.CHECK, 0));
        service.applyAction(betGame.gameId(), 2, intent(betGame, PokerActionType.BET, 100));

        ArgumentCaptor<AcceptedActionHistory> accepted = ArgumentCaptor.forClass(AcceptedActionHistory.class);
        verify(history, times(9)).recordAcceptedAction(eq(20L), accepted.capture());
        assertThat(accepted.getAllValues()).extracting(AcceptedActionHistory::actionType)
                .contains(PokerActionType.FOLD, PokerActionType.CHECK, PokerActionType.CALL,
                        PokerActionType.BET, PokerActionType.RAISE, PokerActionType.ALL_IN);
    }

    @Test
    void wrongUserStaleTurnAndIllegalCheckAreRejectedWithoutHistory() {
        GameRuntimeView view = startHeadsUp(21);

        assertThatThrownBy(() -> service.applyAction(view.gameId(), 2,
                intent(view, PokerActionType.CALL, 0))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.applyAction(view.gameId(), 1,
                new GameActionIntent(UUID.randomUUID(), UUID.randomUUID(), PokerActionType.CALL, 0)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.applyAction(view.gameId(), 1,
                intent(view, PokerActionType.CHECK, 0))).isInstanceOf(RuntimeException.class);

        verify(history, never()).recordAcceptedAction(anyLong(), any());
    }

    @Test
    void deterministicChecksAdvanceEveryStreetWithUniqueFiveCardBoard() {
        GameRuntimeView view = startHeadsUp(31);
        view = service.applyAction(view.gameId(), 1, intent(view, PokerActionType.CALL, 0));
        view = service.applyAction(view.gameId(), 2, intent(view, PokerActionType.CHECK, 0));
        assertThat(view.phase()).isEqualTo(com.ptit.poker.game.domain.state.GamePhase.FLOP);
        assertThat(view.communityCards()).hasSize(3).doesNotHaveDuplicates();
        assertThat(view.currentTurnUserId()).isEqualTo(2);
        assertThat(view.currentBet()).isZero();
        view = checkAround(view); assertThat(view.phase()).isEqualTo(com.ptit.poker.game.domain.state.GamePhase.TURN);
        assertThat(view.communityCards()).hasSize(4).doesNotHaveDuplicates();
        view = checkAround(view); assertThat(view.phase()).isEqualTo(com.ptit.poker.game.domain.state.GamePhase.RIVER);
        assertThat(view.communityCards()).hasSize(5).doesNotHaveDuplicates();
        view = checkAround(view); assertThat(view.handCompleted()).isTrue();
        assertThat(view.communityCards()).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void actionHistoryFailureStopsRuntimeAndPreventsNextHand() {
        GameRuntimeView view = startHeadsUp(41);
        doThrow(new IllegalStateException("history unavailable"))
                .when(history).recordAcceptedAction(anyLong(), any());

        assertThatThrownBy(() -> service.applyAction(view.gameId(), 1,
                intent(view, PokerActionType.CALL, 0))).hasMessageContaining("history unavailable");
        assertThatThrownBy(() -> service.applyAction(view.gameId(), 1,
                intent(view, PokerActionType.CALL, 0)))
                .isInstanceOfSatisfying(GameRuntimeException.class,
                        error -> assertThat(error.code()).isEqualTo("GAME_REQUIRES_RECOVERY"));
        assertThatThrownBy(() -> service.startNextHand(view.gameId())).isInstanceOf(GameRuntimeException.class);
    }

    @Test
    void completionOrRoomSyncFailureStopsRuntimeBeforeNextHand() {
        GameRuntimeView completion = startHeadsUp(42);
        doThrow(new IllegalStateException("completion unavailable"))
                .when(history).completeHand(any(), any(), any(), any());
        assertThatThrownBy(() -> service.applyAction(completion.gameId(), 1,
                intent(completion, PokerActionType.FOLD, 0))).hasMessageContaining("completion unavailable");
        assertThatThrownBy(() -> service.startNextHand(completion.gameId())).isInstanceOf(GameRuntimeException.class);

        reset(history);
        when(history.startHand(anyLong(), anyLong(), anyLong(), anyLong(), any(GameState.class)))
                .thenReturn(new HandHistoryHandle(20, Map.of(1L, 1_000L, 2L, 1_000L)));
        GameRuntimeView synchronization = startHeadsUp(43);
        doThrow(new IllegalStateException("room unavailable"))
                .when(rooms).synchronizeTableChips(eq(43L), any());
        assertThatThrownBy(() -> service.applyAction(synchronization.gameId(), 1,
                intent(synchronization, PokerActionType.FOLD, 0))).hasMessageContaining("room unavailable");
        assertThatThrownBy(() -> service.startNextHand(synchronization.gameId())).isInstanceOf(GameRuntimeException.class);
    }

    @Test
    void sameGameConcurrentRequestsAcceptOnlyOneForTheTurn() throws Exception {
        GameRuntimeView view = startHeadsUp(51);
        CountDownLatch persisted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> { persisted.countDown(); release.await(10, TimeUnit.SECONDS); return null; })
                .when(history).recordAcceptedAction(anyLong(), any());
        GameActionIntent intent = intent(view, PokerActionType.CALL, 0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.applyAction(view.gameId(), 1, intent));
            assertThat(persisted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> service.applyAction(view.gameId(), 1, intent));
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
            verify(history, times(1)).recordAcceptedAction(anyLong(), any());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void blockedGameDoesNotBlockIndependentGame() throws Exception {
        GameRuntimeView firstGame = startHeadsUp(61); GameRuntimeView secondGame = startHeadsUp(62);
        UUID blockedId = UUID.randomUUID();
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            AcceptedActionHistory accepted = invocation.getArgument(1);
            if (blockedId.equals(accepted.clientActionId())) { entered.countDown(); release.await(10, TimeUnit.SECONDS); }
            return null;
        }).when(history).recordAcceptedAction(anyLong(), any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> blocked = executor.submit(() -> service.applyAction(firstGame.gameId(), 1,
                    new GameActionIntent(firstGame.turnId(), blockedId, PokerActionType.CALL, 0)));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> independent = executor.submit(() -> service.applyAction(secondGame.gameId(), 1,
                    intent(secondGame, PokerActionType.CALL, 0)));
            independent.get(10, TimeUnit.SECONDS);
            release.countDown(); blocked.get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void completedHeadsUpHandRotatesDealerAndStartsExactlyOneNextHand() {
        GameRuntimeView first = startHeadsUp(71);
        GameRuntimeView completed = service.applyAction(first.gameId(), 1,
                intent(first, PokerActionType.FOLD, 0));
        assertThat(completed.handCompleted()).isTrue();

        GameRuntimeView second = service.startNextHand(first.gameId());

        assertThat(second.handNumber()).isEqualTo(2);
        assertThat(second.dealerSeat()).isEqualTo(2);
        assertThat(second.smallBlindSeat()).isEqualTo(2);
        assertThat(second.bigBlindSeat()).isEqualTo(1);
        verify(history, times(2)).startHand(eq(10L), anyLong(), eq(50L), eq(100L), any());
        assertThatThrownBy(() -> service.startNextHand(first.gameId())).isInstanceOf(GameRuntimeException.class);
    }

    @Test
    void allInBlindRunoutBustsLoserAndFinishesSessionWithoutOnePlayerHand() {
        when(rooms.load(72)).thenReturn(new RoomGamePort.RoomGameSnapshot(72, 50, 100,
                List.of(seat(1, 1, 50), seat(2, 2, 100))));
        GameRuntimeView completed = service.startGame(72);
        assertThat(completed.handCompleted()).isTrue();
        assertThat(completed.communityCards()).hasSize(5).doesNotHaveDuplicates();
        assertThat(completed.players()).filteredOn(player -> player.tableChips() == 0).hasSize(1);

        GameRuntimeView finished = service.startNextHand(completed.gameId());

        assertThat(finished.sessionFinished()).isTrue();
        verify(sessions).finishSession(10L);
        verify(history, times(1)).startHand(anyLong(), anyLong(), anyLong(), anyLong(), any());
        assertThatThrownBy(() -> service.applyAction(completed.gameId(), 1,
                new GameActionIntent(UUID.randomUUID(), UUID.randomUUID(), PokerActionType.CHECK, 0)))
                .isInstanceOf(GameRuntimeException.class);
    }

    @Test
    void fourGappedSeatsRotateToDealerThreeAndUseClockwisePositions() {
        when(rooms.load(81)).thenReturn(new RoomGamePort.RoomGameSnapshot(81, 50, 100, List.of(
                seat(1, 1, 1_000), seat(3, 3, 1_000), seat(6, 6, 1_000), seat(8, 8, 1_000))));
        GameRuntimeView view = service.startGame(81);
        for (long user : List.of(8L, 1L, 3L)) {
            view = service.applyAction(view.gameId(), user, intent(view, PokerActionType.FOLD, 0));
        }
        view = service.startNextHand(view.gameId());

        assertThat(view.dealerSeat()).isEqualTo(3);
        assertThat(view.smallBlindSeat()).isEqualTo(6);
        assertThat(view.bigBlindSeat()).isEqualTo(8);
        assertThat(view.currentTurnUserId()).isEqualTo(1);
        for (long user : List.of(1L, 3L, 6L)) {
            view = service.applyAction(view.gameId(), user, intent(view, PokerActionType.CALL, 0));
        }
        view = service.applyAction(view.gameId(), 8, intent(view, PokerActionType.CHECK, 0));
        assertThat(view.phase()).isEqualTo(com.ptit.poker.game.domain.state.GamePhase.FLOP);
        assertThat(view.currentTurnUserId()).isEqualTo(6);
        for (long user : List.of(6L, 8L, 1L)) {
            view = service.applyAction(view.gameId(), user, intent(view, PokerActionType.FOLD, 0));
        }

        GameRuntimeView following = service.startNextHand(view.gameId());
        assertThat(following.dealerSeat()).isEqualTo(6);
    }

    @Test
    void shortSmallBlindSnapshotPreservesThirtyChipAllInWithoutFabrication() {
        AtomicReference<List<Object>> blindSnapshot = new AtomicReference<>();
        when(history.startHand(anyLong(), anyLong(), anyLong(), anyLong(), any(GameState.class)))
                .thenAnswer(invocation -> {
                    GameState state = invocation.getArgument(4);
                    var player = state.requirePlayer(1);
                    blindSnapshot.set(List.of(player.tableChips(), player.currentBet(), player.totalCommitted(),
                            player.playerState()));
                    return new HandHistoryHandle(20, Map.of(1L, 30L, 2L, 1_000L));
                });
        when(rooms.load(82)).thenReturn(new RoomGamePort.RoomGameSnapshot(82, 50, 100,
                List.of(seat(1, 1, 30), seat(2, 2, 1_000))));

        service.startGame(82);

        assertThat(blindSnapshot.get()).containsExactly(0L, 30L, 30L,
                com.ptit.poker.game.domain.state.PokerPlayerState.ALL_IN);
    }

    @Test
    void foldedPlayerCannotActAgainOrMutateHistory() {
        when(rooms.load(83)).thenReturn(new RoomGamePort.RoomGameSnapshot(83, 50, 100,
                List.of(seat(1, 1, 1_000), seat(2, 2, 1_000), seat(3, 3, 1_000))));
        GameRuntimeView view = service.startGame(83);
        GameRuntimeView afterFold = service.applyAction(view.gameId(), 1, intent(view, PokerActionType.FOLD, 0));
        long chips = afterFold.players().getFirst().tableChips();

        assertThatThrownBy(() -> service.applyAction(afterFold.gameId(), 1,
                new GameActionIntent(afterFold.turnId(), UUID.randomUUID(), PokerActionType.CHECK, 0)))
                .isInstanceOf(RuntimeException.class);
        assertThat(afterFold.players().getFirst().tableChips()).isEqualTo(chips);
        verify(history, times(1)).recordAcceptedAction(anyLong(), any());
    }

    @Test
    void allInPlayerCannotSubmitAnotherNormalAction() {
        when(rooms.load(84)).thenReturn(new RoomGamePort.RoomGameSnapshot(84, 50, 100,
                List.of(seat(1, 1, 1_000), seat(2, 2, 1_000), seat(3, 3, 1_000))));
        GameRuntimeView view = service.startGame(84);
        GameRuntimeView afterAllIn = service.applyAction(view.gameId(), 1, intent(view, PokerActionType.ALL_IN, 0));

        assertThatThrownBy(() -> service.applyAction(afterAllIn.gameId(), 1,
                new GameActionIntent(afterAllIn.turnId(), UUID.randomUUID(), PokerActionType.CHECK, 0)))
                .isInstanceOf(RuntimeException.class);
        verify(history, times(1)).recordAcceptedAction(anyLong(), any());
    }

    @Test
    void deterministicThreePlayerAllInLeavesBustedPlayerOutOfNextHand() {
        service = new GameRuntimeService(new ActiveGameRegistry(), rooms, sessions, history,
                () -> new Deck(new Random(3)));
        when(rooms.load(85)).thenReturn(new RoomGamePort.RoomGameSnapshot(85, 50, 100,
                List.of(seat(1, 1, 50), seat(2, 2, 100), seat(3, 3, 1_000))));
        GameRuntimeView view = service.startGame(85);
        view = service.applyAction(view.gameId(), 1, intent(view, PokerActionType.ALL_IN, 0));
        view = service.applyAction(view.gameId(), 2, intent(view, PokerActionType.ALL_IN, 0));
        view = service.applyAction(view.gameId(), 3, intent(view, PokerActionType.CHECK, 0));
        List<Long> busted = view.players().stream().filter(player -> player.tableChips() == 0)
                .map(GameRuntimeView.PlayerView::userId).toList();
        assertThat(busted).isNotEmpty();

        GameRuntimeView next = service.startNextHand(view.gameId());

        assertThat(next.handNumber()).isEqualTo(2);
        assertThat(next.players()).extracting(GameRuntimeView.PlayerView::userId).doesNotContainAnyElementsOf(busted);
    }

    @Test
    void timeoutUsesFoldWhenActorOwesChipsAndPersistsAutomaticAction() {
        GameRuntimeView view=startHeadsUp(90);
        var outcome=service.handleTurnTimeout(view.gameId(),view.handId(),view.turnId()).orElseThrow();
        assertThat(outcome.actionType()).isEqualTo(PokerActionType.FOLD);
        assertThat(outcome.automatic()).isTrue();
        assertThat(outcome.clientActionId()).isNull();
        assertThat(outcome.after().handCompleted()).isTrue();
        verify(history).recordAcceptedAction(eq(20L),argThat(action->action.actionType()==PokerActionType.FOLD
                && action.clientActionId()==null));
    }

    @Test
    void timeoutUsesCheckWhenAuthoritativeLegalActionsAllowIt() {
        GameRuntimeView view=startHeadsUp(91);
        view=service.applyAction(view.gameId(),view.currentTurnUserId(),intent(view,PokerActionType.CALL,0));
        var outcome=service.handleTurnTimeout(view.gameId(),view.handId(),view.turnId()).orElseThrow();
        assertThat(outcome.actionType()).isEqualTo(PokerActionType.CHECK);
        assertThat(outcome.automatic()).isTrue();
        assertThat(outcome.after().phase()).isEqualTo(com.ptit.poker.game.domain.state.GamePhase.FLOP);
    }

    @Test
    void staleTurnAndOldHandTimeoutCallbacksAreNoOps() {
        GameRuntimeView first=startHeadsUp(92); UUID oldTurn=first.turnId(); long oldHand=first.handId();
        GameRuntimeView completed=service.applyAction(first.gameId(),first.currentTurnUserId(),intent(first,PokerActionType.FOLD,0));
        assertThat(service.handleTurnTimeout(first.gameId(),oldHand,oldTurn)).isEmpty();
        GameRuntimeView next=service.startNextHand(completed.gameId());
        assertThat(service.handleTurnTimeout(next.gameId(),oldHand,oldTurn)).isEmpty();
        verify(history,times(1)).recordAcceptedAction(anyLong(),any());
    }

    @Test
    void realActionAndTimeoutForSameTurnProduceExactlyOneHistoryAction() throws Exception {
        GameRuntimeView view=startHeadsUp(93); CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> player=executor.submit(()->{ready.countDown();await(go);try{service.applyAction(view.gameId(),
                    view.currentTurnUserId(),intent(view,PokerActionType.FOLD,0));}catch(RuntimeException ignored){}});
            Future<?> timer=executor.submit(()->{ready.countDown();await(go);service.handleTurnTimeout(view.gameId(),view.handId(),view.turnId());});
            assertThat(ready.await(2,TimeUnit.SECONDS)).isTrue();go.countDown();player.get();timer.get();
        }
        verify(history,times(1)).recordAcceptedAction(anyLong(),any());
    }

    @Test
    void disconnectAndReconnectPreserveActiveParticipationAndCommittedChips() {
        GameRuntimeView started=startHeadsUp(94);
        long committed=started.players().stream().filter(player->player.userId()==1).findFirst().orElseThrow().totalCommitted();
        GameRuntimeView disconnected=service.disconnect(1).orElseThrow().view();
        var player=disconnected.players().stream().filter(value->value.userId()==1).findFirst().orElseThrow();
        assertThat(player.participation()).isEqualTo(PokerPlayerState.ACTIVE);
        assertThat(player.connected()).isFalse();assertThat(player.totalCommitted()).isEqualTo(committed);
        assertThat(service.reconnect(1).orElseThrow().view().players().stream()
                .filter(value->value.userId()==1).findFirst().orElseThrow().connected()).isTrue();
        verify(rooms).markDisconnected(94,1);verify(rooms).markReconnected(94,1);
    }

    @Test
    void allInDisconnectPreservesAllInParticipation() {
        when(rooms.load(95)).thenReturn(new RoomGamePort.RoomGameSnapshot(95,50,100,
                List.of(seat(1,1,200),seat(2,2,1_000),seat(3,3,1_000))));
        GameRuntimeView started=service.startGame(95);
        service.applyAction(started.gameId(),1,intent(started,PokerActionType.ALL_IN,0));
        var player=service.disconnect(1).orElseThrow().view().players().stream()
                .filter(value->value.userId()==1).findFirst().orElseThrow();
        assertThat(player.participation()).isEqualTo(PokerPlayerState.ALL_IN);
        assertThat(player.connected()).isFalse();assertThat(player.totalCommitted()).isEqualTo(200);
    }

    @Test
    void graceExpiredDisconnectedPlayerIsExcludedFromNextHand() {
        GameRuntimeView started=startHeadsUp(96);
        GameRuntimeView completed=service.applyAction(started.gameId(),started.currentTurnUserId(),
                intent(started,PokerActionType.FOLD,0));
        service.disconnect(1);assertThat(service.expireReconnect(started.gameId(),1)).isTrue();
        verify(rooms).finalizeActiveGameDeparture(96,1);
        verify(sessions).finishSession(completed.gameSessionId());
        assertThatThrownBy(()->service.currentView(completed.gameId())).isInstanceOf(GameRuntimeException.class);
    }

    @Test
    void completedHeadsUpHandWaitsForLiveGraceAndReconnectKeepsSameSession() {
        GameRuntimeView started=startHeadsUp(97);
        long sessionId=started.gameSessionId();service.disconnect(2);
        GameRuntimeView completed=service.applyAction(started.gameId(),started.currentTurnUserId(),
                intent(started,PokerActionType.FOLD,0));
        when(rooms.load(97)).thenReturn(new RoomGamePort.RoomGameSnapshot(97,50,100,List.of(
                new RoomGamePort.RoomSeat(1,1,completed.players().stream().filter(p->p.userId()==1).findFirst().orElseThrow().tableChips(),RoomPlayerState.PLAYING),
                new RoomGamePort.RoomSeat(2,2,completed.players().stream().filter(p->p.userId()==2).findFirst().orElseThrow().tableChips(),RoomPlayerState.DISCONNECTED))));
        GameRuntimeView waiting=service.startNextHand(started.gameId());
        assertThat(waiting.handNumber()).isOne();assertThat(waiting.sessionFinished()).isFalse();
        verify(sessions,never()).finishSession(sessionId);verify(rooms,never()).finalizeActiveGameDeparture(97,2);

        service.reconnect(2);
        when(rooms.load(97)).thenReturn(new RoomGamePort.RoomGameSnapshot(97,50,100,List.of(
                new RoomGamePort.RoomSeat(1,1,1_000,RoomPlayerState.PLAYING),
                new RoomGamePort.RoomSeat(2,2,1_000,RoomPlayerState.PLAYING))));
        GameRuntimeView next=service.startNextHand(started.gameId());
        assertThat(next.gameSessionId()).isEqualTo(sessionId);assertThat(next.handNumber()).isEqualTo(2);
        verify(sessions,times(1)).startSession(97);
    }

    @Test
    void threePlayerNextHandExcludesDisconnectedMemberButRetainsReconnectability() {
        when(rooms.load(98)).thenReturn(new RoomGamePort.RoomGameSnapshot(98,50,100,List.of(
                seat(1,1,1_000),seat(2,2,1_000),seat(3,3,1_000))));
        GameRuntimeView view=service.startGame(98);service.disconnect(3);
        while(!view.handCompleted())view=service.applyAction(view.gameId(),view.currentTurnUserId(),intent(view,PokerActionType.FOLD,0));
        when(rooms.load(98)).thenReturn(new RoomGamePort.RoomGameSnapshot(98,50,100,List.of(
                new RoomGamePort.RoomSeat(1,1,1_000,RoomPlayerState.PLAYING),
                new RoomGamePort.RoomSeat(2,2,1_000,RoomPlayerState.PLAYING),
                new RoomGamePort.RoomSeat(3,3,1_000,RoomPlayerState.DISCONNECTED))));
        GameRuntimeView next=service.startNextHand(view.gameId());
        assertThat(next.players()).extracting(GameRuntimeView.PlayerView::userId).containsExactly(1L,2L);
        assertThat(service.reconnect(3)).isPresent();
        assertThat(service.currentView(next.gameId()).players()).extracting(GameRuntimeView.PlayerView::userId)
                .doesNotContain(3L);
    }

    private static void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}}

    private GameRuntimeView checkAround(GameRuntimeView view) {
        view = service.applyAction(view.gameId(), view.currentTurnUserId(), intent(view, PokerActionType.CHECK, 0));
        return service.applyAction(view.gameId(), view.currentTurnUserId(), intent(view, PokerActionType.CHECK, 0));
    }

    private void applyFirstAction(long roomId, PokerActionType type, long target) {
        GameRuntimeView view = startHeadsUp(roomId);
        service.applyAction(view.gameId(), 1, intent(view, type, target));
    }

    private GameRuntimeView startHeadsUp(long roomId) {
        when(rooms.load(roomId)).thenReturn(new RoomGamePort.RoomGameSnapshot(roomId, 50, 100,
                List.of(seat(1, 1, 1_000), seat(2, 2, 1_000))));
        return service.startGame(roomId);
    }

    private static GameActionIntent intent(GameRuntimeView view, PokerActionType type, long target) {
        return new GameActionIntent(view.turnId(), UUID.randomUUID(), type, target);
    }

    private static RoomGamePort.RoomGameSnapshot room(RoomGamePort.RoomSeat... seats) {
        return new RoomGamePort.RoomGameSnapshot(1, 50, 100, List.of(seats));
    }
    private static RoomGamePort.RoomSeat seat(long user, int seat, long chips) {
        return new RoomGamePort.RoomSeat(user, seat, chips, RoomPlayerState.READY);
    }
}
