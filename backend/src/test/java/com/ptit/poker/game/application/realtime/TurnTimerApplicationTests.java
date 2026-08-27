package com.ptit.poker.game.application.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ptit.poker.game.api.realtime.*;
import com.ptit.poker.game.api.realtime.GameEventPayloads.Timer;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.domain.betting.*;
import com.ptit.poker.game.domain.state.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class TurnTimerApplicationTests {
    private final GameRuntimeService runtime=mock(GameRuntimeService.class);
    private final RecordingPublisher publisher=new RecordingPublisher();
    private final ManualScheduler scheduler=new ManualScheduler();
    private final MutableClock clock=new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
    private GameRealtimeApplicationService service; private UUID game;
    @BeforeEach void setUp(){game=UUID.randomUUID();service=new GameRealtimeApplicationService(runtime,publisher,clock,scheduler,
            new TurnTimerConfiguration(){public Duration turnTimeout(){return Duration.ofSeconds(10);}
                public Duration updateCadence(){return Duration.ofSeconds(1);}});}

    @Test void actionableStartSchedulesDeadlineAndCadenceAndPublishesSafeAuthoritativeTimer(){
        GameRuntimeView view=view(game,1,UUID.randomUUID(),7,false,false,1L);stubStart(view);
        service.startGame(9);
        assertThat(scheduler.entries).hasSize(2);
        GameRealtimeEvent event=publisher.events.stream().filter(e->e.type()==GameEventType.TIMER_UPDATE).findFirst().orElseThrow();
        Timer timer=(Timer)event.payload();assertThat(timer.turnId()).isEqualTo(view.turnId());assertThat(event.version()).isEqualTo(7);
        assertThat(event.payload().toString()).doesNotContain("holeCards","legalActions");
    }

    @Test void completedOrAllInRunoutStateCreatesNoTimer(){
        GameRuntimeView completed=view(game,1,null,8,true,false,null);stubStart(completed);service.startGame(9);
        assertThat(scheduler.entries).isEmpty();assertThat(publisher.events).noneMatch(e->e.type()==GameEventType.TIMER_UPDATE);
    }

    @Test void acceptedActionCancelsOldTimerAndSchedulesFreshTurn(){
        UUID old=UUID.randomUUID(),fresh=UUID.randomUUID();GameRuntimeView before=view(game,1,old,7,false,false,1L),after=view(game,1,fresh,9,false,false,2L);
        when(runtime.privateView(game,1)).thenReturn(privateView(before,1));when(runtime.privateView(game,2)).thenReturn(privateView(before,2));
        service.announceStartedGame(before);publisher.events.clear();stubAction(before,after);service.handleAction(game,1,message(old));
        assertThat(scheduler.entries.subList(0,2)).allMatch(ManualScheduler.Entry::cancelled);
        assertThat(scheduler.entries.subList(2,4)).noneMatch(ManualScheduler.Entry::cancelled);
        assertThat(publisher.events.stream().filter(e->e.type()==GameEventType.TIMER_UPDATE).map(e->((Timer)e.payload()).turnId()))
                .containsExactly(fresh);
    }

    @Test void deadlinePublishesOneAutomaticActionAndSchedulesFollowingTurn(){
        UUID old=UUID.randomUUID(),fresh=UUID.randomUUID();GameRuntimeView start=view(game,1,old,7,false,false,1L),after=view(game,1,fresh,9,false,false,2L);
        stubStart(start);service.startGame(9);publisher.events.clear();
        when(runtime.handleTurnTimeout(game,30,old)).thenReturn(Optional.of(new GameActionOutcome(start,after,1,1,
                PokerActionType.FOLD,0,0,900,null,null,true)));
        scheduler.deadline(0).run();
        assertThat(publisher.events).filteredOn(e->e.type()==GameEventType.PLAYER_ACTION).singleElement()
                .satisfies(e->assertThat(((com.ptit.poker.game.api.realtime.GameEventPayloads.PlayerAction)e.payload()).automatic()).isTrue());
        assertThat(scheduler.entries).hasSize(4);
    }

    @Test void staleDeadlineNoOpPublishesNoActionAndCannotMutateNextTurn(){
        UUID old=UUID.randomUUID();GameRuntimeView start=view(game,1,old,7,false,false,1L);stubStart(start);service.startGame(9);
        Runnable deadline=scheduler.deadline(0);publisher.events.clear();when(runtime.handleTurnTimeout(game,30,old)).thenReturn(Optional.empty());
        deadline.run();assertThat(publisher.events).noneMatch(e->e.type()==GameEventType.PLAYER_ACTION);
    }

    @Test void independentGamesOwnIndependentTimerHandles(){
        UUID other=UUID.randomUUID();GameRuntimeView first=view(game,1,UUID.randomUUID(),7,false,false,1L);
        GameRuntimeView second=view(other,1,UUID.randomUUID(),7,false,false,1L);
        when(runtime.startGame(9)).thenReturn(first);when(runtime.startGame(10)).thenReturn(second);
        for(GameRuntimeView view:List.of(first,second)){for(var p:view.players())when(runtime.privateView(view.gameId(),p.userId())).thenReturn(privateView(view,p.userId()));}
        service.startGame(9);service.startGame(10);
        assertThat(scheduler.entries).hasSize(4);assertThat(scheduler.entries).noneMatch(ManualScheduler.Entry::cancelled);
    }

    private void stubStart(GameRuntimeView view){when(runtime.startGame(9)).thenReturn(view);for(var p:view.players())when(runtime.privateView(game,p.userId())).thenReturn(privateView(view,p.userId()));}
    private void stubAction(GameRuntimeView before,GameRuntimeView after){when(runtime.isParticipant(game,1)).thenReturn(true);
        when(runtime.applyActionWithOutcome(eq(game),eq(1L),any())).thenReturn(new GameActionOutcome(before,after,1,1,
                PokerActionType.CHECK,0,0,900,UUID.randomUUID(),null,false));when(runtime.privateView(game,2)).thenReturn(privateView(after,2));}
    private GameActionMessage message(UUID turn){return new GameActionMessage(PokerActionType.CHECK,null,turn,UUID.randomUUID());}
    private GamePlayerPrivateView privateView(GameRuntimeView view,long user){return new GamePlayerPrivateView(user,30,view.gameId(),view.turnId(),view.stateVersion(),List.of(),
            new LegalActions(EnumSet.of(PokerActionType.CHECK),0,100,900),900);}
    private GameRuntimeView view(UUID id,long hand,UUID turn,long version,boolean completed,boolean finished,Long actor){return new GameRuntimeView(id,20,9,30,hand,1,1,2,
            completed?GamePhase.FINISHED:GamePhase.PRE_FLOP,actor,turn,version,100,100,50,100,List.of(),List.of(
            new GameRuntimeView.PlayerView(1,1,900,100,100,PokerPlayerState.ACTIVE,2,true,false),
            new GameRuntimeView.PlayerView(2,2,900,100,100,PokerPlayerState.ACTIVE,2,true,false)),completed,finished,false);}
    private static final class RecordingPublisher implements GameRealtimePublisher{final List<GameRealtimeEvent> events=new ArrayList<>();
        public void publishPublic(GameRealtimeEvent event){events.add(event);}public void publishPrivate(long userId,GameRealtimeEvent event){events.add(event);}}
    private static final class ManualScheduler implements TurnTimerScheduler{final List<Entry> entries=new ArrayList<>();
        public Cancellable schedule(Instant deadline,Runnable task){Entry e=new Entry(task);entries.add(e);return e;}
        public Cancellable scheduleAtFixedRate(Duration cadence,Runnable task){Entry e=new Entry(task);entries.add(e);return e;}
        Runnable deadline(int pair){return entries.get(pair).task;}static final class Entry implements Cancellable{final Runnable task;boolean cancelled;Entry(Runnable task){this.task=task;}
            public void cancel(){cancelled=true;}boolean cancelled(){return cancelled;}}}
    private static final class MutableClock extends Clock{private Instant instant;MutableClock(Instant value){instant=value;}
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant;}}
}
