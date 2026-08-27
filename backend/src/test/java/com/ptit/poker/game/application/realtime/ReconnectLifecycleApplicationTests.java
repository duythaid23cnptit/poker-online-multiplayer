package com.ptit.poker.game.application.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.ptit.poker.game.api.realtime.*;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.domain.betting.LegalActions;
import com.ptit.poker.game.domain.card.*;
import com.ptit.poker.game.domain.state.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ReconnectLifecycleApplicationTests {
    private final GameRuntimeService runtime=mock(GameRuntimeService.class);
    private final Publisher publisher=new Publisher();
    private final ManualGraceScheduler grace=new ManualGraceScheduler();
    private final ManualTurnScheduler turns=new ManualTurnScheduler();
    private final Clock clock=Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"),ZoneOffset.UTC);
    private GameRealtimeApplicationService service; private UUID game; private UUID turn;

    @BeforeEach void setUp(){
        game=UUID.randomUUID();turn=UUID.randomUUID();
        service=new GameRealtimeApplicationService(runtime,publisher,clock,turns,
                new TurnTimerConfiguration(){public Duration turnTimeout(){return Duration.ofSeconds(30);}
                    public Duration updateCadence(){return Duration.ofSeconds(1);}},grace,
                new ReconnectGraceConfiguration(Duration.ofSeconds(60)));
    }

    @Test void lastDisconnectSchedulesExactGraceAndPublishesSafeState() {
        GameRuntimeView disconnected=view(false,9);when(runtime.disconnect(1)).thenReturn(Optional.of(
                new GameRuntimeService.GameConnectionTransition(disconnected,1)));
        service.onLastDisconnect(1);
        assertThat(grace.deadline).isEqualTo(clock.instant().plusSeconds(60));
        assertThat(publisher.events).extracting(GameRealtimeEvent::type).containsExactly(GameEventType.GAME_STATE_UPDATE);
        assertThat(disconnected.players().getFirst().participation()).isEqualTo(PokerPlayerState.ALL_IN);
    }

    @Test void reconnectCancelsGraceAndRestoresOnlyOwnPrivateStateWithCurrentVersionAndTurn() {
        GameRuntimeView disconnected=view(false,9),connected=view(true,10);
        when(runtime.disconnect(1)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(disconnected,1)));
        when(runtime.reconnect(1)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(connected,1)));
        Card ace=new Card(Rank.ACE,Suit.SPADES),king=new Card(Rank.KING,Suit.HEARTS);
        when(runtime.privateView(game,1)).thenReturn(new GamePlayerPrivateView(1,5,game,turn,10,
                List.of(ace,king),LegalActions.none(),900));
        service.onLastDisconnect(1);service.onFirstConnection(1);
        assertThat(publisher.privateEvents(1)).isEmpty();
        service.onPrivateSubscription(1);
        assertThat(grace.cancelled).isTrue();
        assertThat(publisher.privateEvents(1)).extracting(GameRealtimeEvent::type)
                .containsExactly(GameEventType.GAME_STATE_UPDATE,GameEventType.HOLE_CARDS,GameEventType.YOUR_TURN);
        assertThat(publisher.privateEvents(2)).isEmpty();
        assertThat(publisher.privateEvents(1)).allMatch(event->event.version()==10);
    }

    @Test void staleGraceCallbackAfterReconnectCannotExpirePlayer() {
        GameRuntimeView disconnected=view(false,9),connected=view(true,10);
        when(runtime.disconnect(1)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(disconnected,1)));
        when(runtime.reconnect(1)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(connected,1)));
        when(runtime.privateView(game,1)).thenReturn(new GamePlayerPrivateView(1,5,game,turn,10,List.of(),LegalActions.none(),900));
        service.onLastDisconnect(1);Runnable stale=grace.task;service.onFirstConnection(1);service.onPrivateSubscription(1);stale.run();
        verify(runtime,never()).expireReconnect(any(),anyLong());
    }

    @Test void graceExpiryMarksCurrentGenerationAndLateConnectDoesNotRestore() {
        GameRuntimeView disconnected=view(false,9);
        when(runtime.disconnect(1)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(disconnected,1)));
        when(runtime.expireReconnect(game,1)).thenReturn(true);
        service.onLastDisconnect(1);grace.task.run();service.onFirstConnection(1);
        verify(runtime).expireReconnect(game,1);verify(runtime,never()).reconnect(1);
    }

    @Test void earlierGraceExpiryDoesNotCancelCurrentTurnDeadline() {
        GameRuntimeView connected=activeView(true,8),disconnected=activeView(false,9),after=activeView(false,10);
        when(runtime.privateView(game,1)).thenReturn(new GamePlayerPrivateView(1,5,game,turn,8,List.of(),LegalActions.none(),900));
        when(runtime.privateView(game,2)).thenReturn(new GamePlayerPrivateView(2,5,game,turn,8,List.of(),LegalActions.none(),900));
        when(runtime.disconnect(1)).thenReturn(Optional.of(new GameRuntimeService.GameConnectionTransition(disconnected,1)));
        when(runtime.expireReconnect(game,1)).thenReturn(true);
        when(runtime.handleTurnTimeout(game,5,turn)).thenReturn(Optional.of(new GameActionOutcome(connected,after,1,1,
                com.ptit.poker.game.domain.betting.PokerActionType.FOLD,0,0,900,null,null,true)));

        service.announceStartedGame(connected);service.onLastDisconnect(1);grace.task.run();

        assertThat(turns.deadline.cancelled).isFalse();
        turns.deadline.task.run();
        verify(runtime).handleTurnTimeout(game,5,turn);
        assertThat(publisher.events).filteredOn(event->event.type()==GameEventType.PLAYER_ACTION).hasSize(1);
    }

    private GameRuntimeView view(boolean connected,long version){return new GameRuntimeView(game,3,4,5,1,1,1,2,
            GamePhase.PRE_FLOP,1L,turn,version,100,100,50,100,List.of(),List.of(
            new GameRuntimeView.PlayerView(1,1,900,100,100,PokerPlayerState.ALL_IN,2,connected,false),
            new GameRuntimeView.PlayerView(2,2,900,100,100,PokerPlayerState.ACTIVE,2,true,false)),false,false,false);}
    private GameRuntimeView activeView(boolean connected,long version){return new GameRuntimeView(game,3,4,5,1,1,1,2,
            GamePhase.PRE_FLOP,1L,turn,version,100,100,50,100,List.of(),List.of(
            new GameRuntimeView.PlayerView(1,1,900,100,100,PokerPlayerState.ACTIVE,2,connected,false),
            new GameRuntimeView.PlayerView(2,2,900,100,100,PokerPlayerState.ACTIVE,2,true,false)),false,false,false);}
    private static final class ManualGraceScheduler implements ReconnectGraceScheduler{
        Instant deadline;Runnable task;boolean cancelled;
        public Cancellable schedule(Instant deadline,Runnable task){this.deadline=deadline;this.task=task;return ()->cancelled=true;}
    }
    private static final class ManualTurnScheduler implements TurnTimerScheduler{
        Entry deadline;
        public Cancellable schedule(Instant deadline,Runnable task){this.deadline=new Entry(task);return this.deadline;}
        public Cancellable scheduleAtFixedRate(Duration cadence,Runnable task){return new Entry(task);}
        private static final class Entry implements Cancellable{final Runnable task;boolean cancelled;
            private Entry(Runnable task){this.task=task;}public void cancel(){cancelled=true;}}
    }
    private static final class Publisher implements GameRealtimePublisher{
        final List<GameRealtimeEvent> events=new ArrayList<>();final Map<Long,List<GameRealtimeEvent>> privateEvents=new HashMap<>();
        public void publishPublic(GameRealtimeEvent event){events.add(event);}
        public void publishPrivate(long userId,GameRealtimeEvent event){privateEvents.computeIfAbsent(userId,x->new ArrayList<>()).add(event);}
        List<GameRealtimeEvent> privateEvents(long userId){return privateEvents.getOrDefault(userId,List.of());}
    }
}
