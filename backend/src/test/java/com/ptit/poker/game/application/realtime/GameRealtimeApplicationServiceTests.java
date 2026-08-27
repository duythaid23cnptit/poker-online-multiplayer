package com.ptit.poker.game.application.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ptit.poker.game.api.realtime.*;
import com.ptit.poker.game.api.realtime.GameEventPayloads.*;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.domain.betting.*;
import com.ptit.poker.game.domain.card.*;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import com.ptit.poker.game.domain.state.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameRealtimeApplicationServiceTests {
    private final GameRuntimeService runtime = mock(GameRuntimeService.class);
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final UUID gameId = UUID.randomUUID(), turnId = UUID.randomUUID();
    private GameRealtimeApplicationService service;
    private final TurnTimerScheduler timers=mock(TurnTimerScheduler.class);
    @BeforeEach void setUp() {
        when(timers.schedule(any(),any())).thenReturn(()->{});when(timers.scheduleAtFixedRate(any(),any())).thenReturn(()->{});
        service = new GameRealtimeApplicationService(runtime, publisher,
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),timers,
            new TurnTimerConfiguration(){public Duration turnTimeout(){return Duration.ofSeconds(30);}
                public Duration updateCadence(){return Duration.ofSeconds(1);}}); }

    @Test void startPublishesPublicBeforePrivateAndNeverLeaksHoleCards() {
        GameRuntimeView view = view(GamePhase.PRE_FLOP, List.of(), 7, false, 1L);
        when(runtime.startGame(9)).thenReturn(view);
        when(runtime.privateView(gameId, 1)).thenReturn(privateView(1, List.of(card(Rank.ACE), card(Rank.KING)), true));
        when(runtime.privateView(gameId, 2)).thenReturn(privateView(2, List.of(card(Rank.QUEEN), card(Rank.JACK)), false));
        service.startGame(9);
        assertThat(publisher.all).extracting(Sent::type).containsExactly(GameEventType.GAME_STARTED,
                GameEventType.HAND_STARTED, GameEventType.HOLE_CARDS, GameEventType.HOLE_CARDS,
                GameEventType.YOUR_TURN,GameEventType.TIMER_UPDATE);
        assertThat(publisher.all.get(2).userId()).isEqualTo(1);
        assertThat(publisher.all.get(3).userId()).isEqualTo(2);
        assertThat(publisher.all.get(4).userId()).isEqualTo(1);
        assertThat(publisher.publicEvents()).allSatisfy(event -> assertThat(event.payload()).isNotInstanceOf(HoleCards.class));
    }

    @Test void acceptedActionPublishesOneAuthoritativeActionAndPreservesIdsAndVersion() {
        UUID clientId = UUID.randomUUID(); GameRuntimeView before = view(GamePhase.PRE_FLOP, List.of(), 7, false, 1L);
        GameRuntimeView after = view(GamePhase.PRE_FLOP, List.of(), 9, false, 2L);
        when(runtime.isParticipant(gameId, 1)).thenReturn(true);
        when(runtime.applyActionWithOutcome(eq(gameId), eq(1L), any())).thenReturn(new GameActionOutcome(before, after,
                1, 1, PokerActionType.CALL, 100, 100, 900, clientId, null,false));
        when(runtime.privateView(gameId, 2)).thenReturn(privateView(2, List.of(), true));
        service.handleAction(gameId, 1, new GameActionMessage(PokerActionType.CALL, null, turnId, clientId));
        assertThat(publisher.publicEvents()).extracting(GameRealtimeEvent::type)
                .containsExactly(GameEventType.PLAYER_ACTION, GameEventType.GAME_STATE_UPDATE,GameEventType.TIMER_UPDATE);
        GameRealtimeEvent action = publisher.publicEvents().getFirst();
        assertThat(action.version()).isEqualTo(9);
        assertThat((PlayerAction) action.payload()).extracting(PlayerAction::amount, PlayerAction::clientActionId)
                .containsExactly(100L, clientId);
        assertThat(publisher.all).filteredOn(sent -> sent.type() == GameEventType.PLAYER_ACTION).hasSize(1);
    }

    @Test void rejectedAndSpectatorActionsPublishOnlyPrivateSafeError() {
        GameRuntimeView current = view(GamePhase.PRE_FLOP, List.of(), 7, false, 1L);
        when(runtime.currentView(gameId)).thenReturn(current);
        when(runtime.isParticipant(gameId, 99)).thenReturn(false);
        service.handleAction(gameId, 99, new GameActionMessage(PokerActionType.CHECK, null, turnId, UUID.randomUUID()));
        assertThat(publisher.publicEvents()).isEmpty();
        assertThat(publisher.all).singleElement().satisfies(sent -> {
            assertThat(sent.userId()).isEqualTo(99); assertThat(sent.type()).isEqualTo(GameEventType.COMMAND_ERROR);
            assertThat(((CommandError) sent.event().payload()).code()).isEqualTo("ACTION_REJECTED");
        });
        verify(runtime, never()).applyActionWithOutcome(any(), anyLong(), any());
    }

    @Test void streetAdvanceOrdersActionCommunityStateThenPrivateTurn() {
        GameRuntimeView before = view(GamePhase.PRE_FLOP, List.of(), 7, false, 1L);
        GameRuntimeView after = view(GamePhase.FLOP, List.of(card(Rank.TWO), card(Rank.THREE), card(Rank.FOUR)), 9, false, 2L);
        accepted(before, after, null);
        assertThat(publisher.all).extracting(Sent::type).containsExactly(GameEventType.PLAYER_ACTION,
                GameEventType.COMMUNITY_CARDS, GameEventType.GAME_STATE_UPDATE, GameEventType.YOUR_TURN,
                GameEventType.TIMER_UPDATE);
        assertThat(((CommunityCards) publisher.all.get(1).event().payload()).cards()).hasSize(3).doesNotHaveDuplicates();
    }

    @Test void turnAndRiverPublishAuthoritativeBoardOrder() {
        for (var scenario : List.of(
                List.of(GamePhase.FLOP, GamePhase.TURN, 3, 4),
                List.of(GamePhase.TURN, GamePhase.RIVER, 4, 5))) {
            publisher.all.clear();
            List<Card> beforeCards = cards((int) scenario.get(2)); List<Card> afterCards = cards((int) scenario.get(3));
            accepted(view((GamePhase) scenario.get(0), beforeCards, 7, false, 1L),
                    view((GamePhase) scenario.get(1), afterCards, 9, false, 2L), null);
            assertThat(((CommunityCards) publisher.all.get(1).event().payload()).cards()).containsExactlyElementsOf(afterCards);
        }
    }

    @Test void earlyFoldHasNoCommunityOrShowdownAndPublishesTerminalOrder() {
        accepted(view(GamePhase.PRE_FLOP, List.of(), 7, false, 1L),
                view(GamePhase.FINISHED, List.of(), 9, true, null), settlement(true));
        assertThat(publisher.publicEvents()).extracting(GameRealtimeEvent::type).containsExactly(
                GameEventType.PLAYER_ACTION, GameEventType.GAME_STATE_UPDATE,
                GameEventType.GAME_RESULT, GameEventType.HAND_FINISHED);
    }

    @Test void showdownPublishesFinalBoardBeforeSafeShowdownResultAndFinish() {
        accepted(view(GamePhase.RIVER, cards(4), 7, false, 1L),
                view(GamePhase.SHOWDOWN, cards(5), 9, true, null), settlement(false));
        assertThat(publisher.publicEvents()).extracting(GameRealtimeEvent::type).containsExactly(
                GameEventType.PLAYER_ACTION, GameEventType.COMMUNITY_CARDS, GameEventType.GAME_STATE_UPDATE,
                GameEventType.SHOWDOWN, GameEventType.GAME_RESULT, GameEventType.HAND_FINISHED);
        assertThat(publisher.publicEvents()).noneMatch(event -> event.payload() instanceof HoleCards);
    }

    @Test void staleTurnPublishesPrivateErrorAndNoPublicOrDuplicateAction() {
        when(runtime.isParticipant(gameId, 1)).thenReturn(true); when(runtime.currentView(gameId)).thenReturn(view(GamePhase.PRE_FLOP, List.of(), 7, false, 1L));
        when(runtime.applyActionWithOutcome(any(), anyLong(), any())).thenThrow(new BettingRuleViolationException("turnId is stale"));
        service.handleAction(gameId, 1, new GameActionMessage(PokerActionType.CALL, null, UUID.randomUUID(), UUID.randomUUID()));
        assertThat(publisher.publicEvents()).isEmpty();
        assertThat(((CommandError) publisher.all.getFirst().event().payload()).code()).isEqualTo("STALE_TURN");
    }

    private void accepted(GameRuntimeView before, GameRuntimeView after, HandSettlementResult settlement) {
        when(runtime.isParticipant(gameId, 1)).thenReturn(true);
        when(runtime.applyActionWithOutcome(eq(gameId), eq(1L), any())).thenReturn(new GameActionOutcome(before, after,
                1, 1, PokerActionType.CHECK, 0, 0, 900, UUID.randomUUID(), settlement,false));
        if (!after.handCompleted()) when(runtime.privateView(gameId, after.currentTurnUserId())).thenReturn(privateView(after.currentTurnUserId(), List.of(), true));
        service.handleAction(gameId, 1, new GameActionMessage(PokerActionType.CHECK, null, turnId, UUID.randomUUID()));
    }
    private GameRuntimeView view(GamePhase phase, List<Card> board, long version, boolean completed, Long actor) {
        return new GameRuntimeView(gameId, 20, 9, 30, 1, 1, 1, 2, phase, actor,
                actor == null ? null : turnId, version, 100, 100, 50, 100, board,
                List.of(new GameRuntimeView.PlayerView(1, 1, 900, 100, 100, PokerPlayerState.ACTIVE, 2, true, false),
                        new GameRuntimeView.PlayerView(2, 2, 900, 100, 100, PokerPlayerState.ACTIVE, 2, true, false)),
                completed, false, false);
    }
    private GamePlayerPrivateView privateView(long user, List<Card> cards, boolean canCheck) {
        LegalActions legal = canCheck ? new LegalActions(EnumSet.of(PokerActionType.CHECK), 0, 100, 900) : LegalActions.none();
        return new GamePlayerPrivateView(user, 30, gameId, turnId, 7, cards, legal, 900);
    }
    private HandSettlementResult settlement(boolean fold) { return new HandSettlementResult(Map.of(), List.of(), List.of(),
            List.of(), Map.of(), Map.of(), 0, fold, 2_000, 2_000); }
    private static Card card(Rank rank) { return new Card(rank, Suit.SPADES); }
    private static List<Card> cards(int count) { return Arrays.asList(Rank.values()).subList(0, count).stream().map(GameRealtimeApplicationServiceTests::card).toList(); }

    private record Sent(Long userId, GameRealtimeEvent event) { GameEventType type() { return event.type(); } }
    private static final class RecordingPublisher implements GameRealtimePublisher {
        final List<Sent> all = new ArrayList<>();
        public void publishPublic(GameRealtimeEvent event) { all.add(new Sent(null, event)); }
        public void publishPrivate(long userId, GameRealtimeEvent event) { all.add(new Sent(userId, event)); }
        List<GameRealtimeEvent> publicEvents() { return all.stream().filter(sent -> sent.userId == null).map(Sent::event).toList(); }
    }
}
