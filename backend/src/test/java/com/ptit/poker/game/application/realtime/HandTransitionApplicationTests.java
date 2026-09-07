package com.ptit.poker.game.application.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ptit.poker.game.api.realtime.*;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.domain.betting.*;
import com.ptit.poker.game.domain.card.*;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import com.ptit.poker.game.domain.state.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HandTransitionApplicationTests {
    private final GameRuntimeService runtime = mock(GameRuntimeService.class);
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final ManualHandScheduler transitions = new ManualHandScheduler();
    private final ManualGraceScheduler grace = new ManualGraceScheduler();
    private final TurnTimerScheduler timers = mock(TurnTimerScheduler.class);
    private final UUID gameId = UUID.randomUUID(), turnId = UUID.randomUUID();
    private GameRealtimeApplicationService service;

    @BeforeEach void setUp() {
        when(timers.schedule(any(), any())).thenReturn(() -> {});
        when(timers.scheduleAtFixedRate(any(), any())).thenReturn(() -> {});
        service = new GameRealtimeApplicationService(runtime, publisher,
                Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC), timers,
                new TurnTimerConfiguration() {
                    public Duration turnTimeout() { return Duration.ofSeconds(30); }
                    public Duration updateCadence() { return Duration.ofSeconds(1); }
                }, grace, new ReconnectGraceConfiguration(Duration.ofSeconds(60)),
                view -> {}, transitions, () -> Duration.ofSeconds(3));
    }

    @Test void finishedHandSchedulesExactlyOneDelayedTransitionAndDoesNotRunImmediately() {
        completeHand();
        completeHand();

        assertThat(transitions.delay).isEqualTo(Duration.ofSeconds(3));
        assertThat(transitions.scheduled).isOne();
        verify(runtime, never()).startNextHand(gameId);
    }

    @Test void scheduledTransitionPublishesSecondHandAfterFinishedLifecycle() {
        completeHand();
        GameRuntimeView next = view(32, 2, false, 1L);
        when(runtime.startNextHand(gameId)).thenReturn(next);
        when(runtime.privateView(eq(gameId), anyLong())).thenAnswer(invocation -> privateView(invocation.getArgument(1)));

        transitions.run();

        assertThat(publisher.publicTypes()).containsSequence(GameEventType.GAME_RESULT, GameEventType.HAND_FINISHED,
                GameEventType.HAND_STARTED);
        assertThat(publisher.all).extracting(Sent::type).containsSequence(GameEventType.HAND_STARTED,
                GameEventType.HOLE_CARDS, GameEventType.HOLE_CARDS, GameEventType.YOUR_TURN,
                GameEventType.TIMER_UPDATE);
        assertThat(service.hasPendingHandTransition(gameId)).isFalse();
    }

    @Test void initialAllInRunoutWithStacksTenAndZeroFinishesTheSession() {
        assertInitialAllInBustFinishesSession(10, 0);
    }

    @Test void initialAllInRunoutWithStacksZeroAndTenFinishesTheSession() {
        assertInitialAllInBustFinishesSession(0, 10);
    }

    @Test void reconnectGraceWaitDoesNotPublishFalseHandOrInstallTurnTimer() {
        completeHand();
        when(runtime.startNextHand(gameId)).thenReturn(view(31, 1, true, null));
        clearInvocations(timers);
        publisher.all.clear();

        transitions.run();

        assertThat(publisher.all).isEmpty();
        verifyNoInteractions(timers);
    }

    @Test void transientTransitionFailureRetainsLifecycleWorkAndRetriesOnce() {
        completeHand();
        GameRuntimeView next = view(32, 2, false, 1L);
        when(runtime.startNextHand(gameId)).thenThrow(new IllegalStateException("commit failed")).thenReturn(next);
        when(runtime.privateView(eq(gameId), anyLong())).thenAnswer(invocation -> privateView(invocation.getArgument(1)));
        clearInvocations(timers);
        publisher.all.clear();

        transitions.run();

        assertThat(publisher.all).isEmpty();
        verifyNoInteractions(timers);
        assertThat(transitions.scheduled).isEqualTo(2);
        assertThat(service.hasPendingHandTransition(gameId)).isTrue();
        transitions.run();
        verify(runtime, times(2)).startNextHand(gameId);
        assertThat(service.hasPendingHandTransition(gameId)).isFalse();
    }

    @Test void sessionFinishPublishesOnlyFinalState() {
        completeHand();
        when(runtime.startNextHand(gameId)).thenReturn(new GameRuntimeView(gameId, 20, 9, 31, 1,
                1, 1, 2, GamePhase.FINISHED, null, null, 8, 0, 20, 10, 20,
                List.of(), players(), true, true, false));
        publisher.all.clear();

        transitions.run();

        assertThat(publisher.publicTypes()).containsExactly(GameEventType.GAME_STATE_UPDATE);
        assertThat(transitions.cancelled).isTrue();
        assertThat(service.hasPendingHandTransition(gameId)).isFalse();
    }

    @Test void foldCompletionWithOneEligiblePlayerFinishesOnceAndClearsTheTransition() {
        GameRuntimeView before = viewWithStacks(31, 1, false, false, 1L, 5, 5);
        GameRuntimeView completed = viewWithStacks(31, 1, true, false, null, 10, 0);
        GameRuntimeView finished = viewWithStacks(31, 1, true, true, null, 10, 0);
        when(runtime.isParticipant(gameId, 1)).thenReturn(true);
        when(runtime.applyActionWithOutcome(eq(gameId), eq(1L), any())).thenReturn(new GameActionOutcome(
                before, completed, 1, 1, PokerActionType.FOLD, 0, 5, 10, UUID.randomUUID(), settlement(), false));
        when(runtime.currentView(gameId)).thenReturn(completed);
        when(runtime.startNextHand(gameId)).thenReturn(finished);

        service.handleAction(gameId, 1,
                new GameActionMessage(PokerActionType.FOLD, null, turnId, UUID.randomUUID()));
        transitions.run();

        verify(runtime, times(1)).startNextHand(gameId);
        assertThat(publisher.publicTypes()).containsSequence(GameEventType.HAND_FINISHED,
                GameEventType.GAME_STATE_UPDATE);
        assertThat(service.hasPendingHandTransition(gameId)).isFalse();
        transitions.run();
        verify(runtime, times(1)).startNextHand(gameId);
    }

    @Test void completionPublicationFailureCannotDropThePendingLifecycleTask() {
        GameRealtimePublisher failingPublisher = mock(GameRealtimePublisher.class);
        GameRealtimeApplicationService failingService = new GameRealtimeApplicationService(runtime, failingPublisher,
                Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC), timers,
                new TurnTimerConfiguration() {
                    public Duration turnTimeout() { return Duration.ofSeconds(30); }
                    public Duration updateCadence() { return Duration.ofSeconds(1); }
                }, grace, new ReconnectGraceConfiguration(Duration.ofSeconds(60)),
                view -> {}, transitions, () -> Duration.ofSeconds(3));
        GameRuntimeView before = viewWithStacks(31, 1, false, false, 1L, 5, 5);
        GameRuntimeView completed = viewWithStacks(31, 1, true, false, null, 10, 0);
        when(runtime.isParticipant(gameId, 1)).thenReturn(true);
        when(runtime.applyActionWithOutcome(eq(gameId), eq(1L), any())).thenReturn(new GameActionOutcome(
                before, completed, 1, 1, PokerActionType.FOLD, 0, 5, 10, UUID.randomUUID(), settlement(), false));
        when(runtime.currentView(gameId)).thenReturn(completed);
        doThrow(new IllegalStateException("broker unavailable")).when(failingPublisher).publishPublic(any());

        failingService.handleAction(gameId, 1,
                new GameActionMessage(PokerActionType.FOLD, null, turnId, UUID.randomUUID()));

        assertThat(failingService.hasPendingHandTransition(gameId)).isTrue();
        assertThat(transitions.scheduled).isOne();
    }

    @Test void reconnectAfterGraceWaitSchedulesOneNewAttemptThatStartsNextHand() {
        GameRuntimeView active = view(31, 1, false, 1L);
        GameRuntimeView completed = view(31, 1, true, null);
        when(runtime.disconnect(2)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(active, 2)));
        service.onLastDisconnect(2);
        completeHand();
        when(runtime.startNextHand(gameId)).thenReturn(completed);
        transitions.run();
        when(runtime.reconnect(2)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(completed, 2)));

        service.onFirstConnection(2);
        when(runtime.startNextHand(gameId)).thenReturn(view(32, 2, false, 1L));
        when(runtime.privateView(eq(gameId), anyLong())).thenAnswer(invocation -> privateView(invocation.getArgument(1)));
        publisher.all.clear();
        transitions.run();

        assertThat(transitions.scheduled).isEqualTo(2);
        assertThat(publisher.publicTypes()).containsExactly(GameEventType.HAND_STARTED, GameEventType.TIMER_UPDATE);
    }

    @Test void graceExpiryFinishesSessionAndCancelsPendingTransition() {
        GameRuntimeView active = view(31, 1, false, 1L);
        GameRuntimeView finished = new GameRuntimeView(gameId, 20, 9, 31, 1, 1, 1, 2,
                GamePhase.FINISHED, null, null, 8, 0, 20, 10, 20, List.of(), players(), true, true, false);
        when(runtime.disconnect(2)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(active, 2)));
        service.onLastDisconnect(2);
        completeHand();
        when(runtime.expireReconnectWithView(gameId, 2)).thenReturn(Optional.of(finished));
        publisher.all.clear();

        grace.run();
        transitions.run();

        assertThat(publisher.publicTypes()).containsExactly(GameEventType.GAME_STATE_UPDATE);
        verify(runtime, never()).startNextHand(gameId);
    }

    private void completeHand() {
        GameRuntimeView before = view(31, 1, false, 1L);
        GameRuntimeView completed = view(31, 1, true, null);
        when(runtime.isParticipant(gameId, 1)).thenReturn(true);
        when(runtime.applyActionWithOutcome(eq(gameId), eq(1L), any())).thenReturn(new GameActionOutcome(before,
                completed, 1, 1, PokerActionType.FOLD, 0, 10, 990, UUID.randomUUID(), settlement(), false));
        when(runtime.currentView(gameId)).thenReturn(completed);
        service.handleAction(gameId, 1, new GameActionMessage(PokerActionType.FOLD, null, turnId, UUID.randomUUID()));
    }

    private void assertInitialAllInBustFinishesSession(long firstStack, long secondStack) {
        GameRuntimeView completed = viewWithStacks(31, 1, true, false, null, firstStack, secondStack);
        GameRuntimeView finished = viewWithStacks(31, 1, true, true, null, firstStack, secondStack);
        when(runtime.startGame(9, 7)).thenReturn(completed);
        when(runtime.currentView(gameId)).thenReturn(completed);
        when(runtime.completedSettlement(gameId, completed.handId())).thenReturn(Optional.of(settlement(false)));
        when(runtime.startNextHand(gameId)).thenReturn(finished);
        when(runtime.privateView(eq(gameId), anyLong())).thenAnswer(invocation -> privateView(invocation.getArgument(1)));

        service.startGame(9, 7);

        assertThat(publisher.publicTypes()).containsSequence(GameEventType.GAME_STATE_UPDATE,
                GameEventType.SHOWDOWN, GameEventType.GAME_RESULT, GameEventType.HAND_FINISHED);
        assertThat(transitions.scheduled).isOne();
        assertThat(service.hasPendingHandTransition(gameId)).isTrue();

        transitions.run();

        verify(runtime, times(1)).startNextHand(gameId);
        assertThat(publisher.publicTypes().getLast()).isEqualTo(GameEventType.GAME_STATE_UPDATE);
        GameEventPayloads.State finalState = (GameEventPayloads.State) publisher.all.stream()
                .filter(sent -> sent.userId() == null && sent.type() == GameEventType.GAME_STATE_UPDATE)
                .map(sent -> sent.event().payload()).reduce((first, second) -> second).orElseThrow();
        assertThat(finalState.sessionFinished()).isTrue();
        assertThat(finalState.players()).extracting(GameEventPayloads.PublicPlayer::tableChips)
                .containsExactly(firstStack, secondStack);
        assertThat(transitions.cancelled).isTrue();
        assertThat(service.hasPendingHandTransition(gameId)).isFalse();
        transitions.run();
        verify(runtime, times(1)).startNextHand(gameId);
    }

    private GameRuntimeView view(long handId, long handNumber, boolean completed, Long actor) {
        return viewWithStacks(handId, handNumber, completed, false, actor, 990, 1_010);
    }

    private GameRuntimeView viewWithStacks(long handId, long handNumber, boolean completed, boolean finished,
                                           Long actor, long firstStack, long secondStack) {
        return new GameRuntimeView(gameId, 20, 9, handId, handNumber, 1, 1, 2,
                completed ? GamePhase.FINISHED : GamePhase.PRE_FLOP, actor, actor == null ? null : turnId,
                completed ? 8 : 7, 10, 20, 10, 20, List.of(), players(firstStack, secondStack),
                completed, finished, false);
    }

    private List<GameRuntimeView.PlayerView> players() {
        return players(990, 1_010);
    }

    private List<GameRuntimeView.PlayerView> players(long firstStack, long secondStack) {
        return List.of(new GameRuntimeView.PlayerView(1, 1, firstStack, 10, 10, PokerPlayerState.ACTIVE, 2, true, false),
                new GameRuntimeView.PlayerView(2, 2, secondStack, 0, 0, PokerPlayerState.ACTIVE, 2, true, false));
    }

    private GamePlayerPrivateView privateView(long userId) {
        return new GamePlayerPrivateView(userId, 32, gameId, turnId, 0,
                List.of(new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES)),
                userId == 1 ? new LegalActions(EnumSet.of(PokerActionType.CHECK), 0, 20, 990) : LegalActions.none(),
                userId == 1 ? 990 : 1_010);
    }

    private HandSettlementResult settlement() {
        return settlement(true);
    }

    private HandSettlementResult settlement(boolean foldOnly) {
        return new HandSettlementResult(Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), 0,
                foldOnly, 2_000, 2_000);
    }

    private record Sent(Long userId, GameRealtimeEvent event) { GameEventType type() { return event.type(); } }
    private static final class RecordingPublisher implements GameRealtimePublisher {
        final List<Sent> all = new ArrayList<>();
        public void publishPublic(GameRealtimeEvent event) { all.add(new Sent(null, event)); }
        public void publishPrivate(long userId, GameRealtimeEvent event) { all.add(new Sent(userId, event)); }
        List<GameEventType> publicTypes() { return all.stream().filter(sent -> sent.userId == null)
                .map(Sent::type).toList(); }
    }
    private static final class ManualHandScheduler implements HandTransitionScheduler {
        Duration delay; Runnable task; int scheduled; boolean cancelled;
        public Cancellable schedule(Duration delay, Runnable task) {
            this.delay = delay; this.task = task; scheduled++; cancelled = false;
            return () -> cancelled = true;
        }
        void run() { if (!cancelled) task.run(); }
    }
    private static final class ManualGraceScheduler implements ReconnectGraceScheduler {
        Runnable task;
        public Cancellable schedule(Instant deadline, Runnable task) { this.task = task; return () -> {}; }
        void run() { task.run(); }
    }
}
