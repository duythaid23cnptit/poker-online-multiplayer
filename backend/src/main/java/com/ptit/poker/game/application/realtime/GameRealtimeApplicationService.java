package com.ptit.poker.game.application.realtime;

import com.ptit.poker.game.api.realtime.*;
import com.ptit.poker.game.api.realtime.GameEventPayloads.*;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.domain.betting.BettingRuleViolationException;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service @Profile("!bootstrap")
public class GameRealtimeApplicationService {
    private static final Logger LOGGER=LoggerFactory.getLogger(GameRealtimeApplicationService.class);
    private final GameRuntimeService runtime;
    private final GameRealtimePublisher publisher;
    private final Clock clock;
    private final TurnTimerScheduler timers;
    private final TurnTimerConfiguration timerConfiguration;
    private final ReconnectGraceScheduler reconnectScheduler;
    private final ReconnectGraceConfiguration reconnectConfiguration;
    private final RoomGameDiscoveryPublisher roomDiscovery;
    private final HandTransitionScheduler handTransitions;
    private final HandTransitionConfiguration handTransitionConfiguration;
    private final ConcurrentHashMap<UUID, ActiveTimer> activeTimers=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> eventLocks=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ActiveGrace> activeGrace=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, GameRuntimeView> pendingRestorations=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PendingHandTransition> pendingHandTransitions=new ConcurrentHashMap<>();
    private final AtomicLong graceGeneration=new AtomicLong();
    private final AtomicLong handTransitionGeneration=new AtomicLong();

    @Autowired
    public GameRealtimeApplicationService(GameRuntimeService runtime, GameRealtimePublisher publisher, Clock clock,
                                          TurnTimerScheduler timers, TurnTimerConfiguration timerConfiguration,
                                          ReconnectGraceScheduler reconnectScheduler,
                                          ReconnectGraceConfiguration reconnectConfiguration,
                                          RoomGameDiscoveryPublisher roomDiscovery,
                                          HandTransitionScheduler handTransitions,
                                          HandTransitionConfiguration handTransitionConfiguration) {
        this.runtime = runtime; this.publisher = publisher; this.clock = clock;
        this.timers=timers; this.timerConfiguration=timerConfiguration;
        this.reconnectScheduler=reconnectScheduler; this.reconnectConfiguration=reconnectConfiguration;
        this.roomDiscovery=roomDiscovery;
        this.handTransitions=handTransitions; this.handTransitionConfiguration=handTransitionConfiguration;
    }

    GameRealtimeApplicationService(GameRuntimeService runtime, GameRealtimePublisher publisher, Clock clock,
                                   TurnTimerScheduler timers, TurnTimerConfiguration timerConfiguration) {
        this(runtime,publisher,clock,timers,timerConfiguration,(deadline,task)->()->{},
                new ReconnectGraceConfiguration(Duration.ofSeconds(60)), view -> {}, (delay,task)->()->{},
                () -> Duration.ofSeconds(6));
    }

    GameRealtimeApplicationService(GameRuntimeService runtime, GameRealtimePublisher publisher, Clock clock,
                                   TurnTimerScheduler timers, TurnTimerConfiguration timerConfiguration,
                                   ReconnectGraceScheduler reconnectScheduler,
                                   ReconnectGraceConfiguration reconnectConfiguration) {
        this(runtime, publisher, clock, timers, timerConfiguration, reconnectScheduler,
                reconnectConfiguration, view -> {}, (delay,task)->()->{}, () -> Duration.ofSeconds(6));
    }

    GameRealtimeApplicationService(GameRuntimeService runtime, GameRealtimePublisher publisher, Clock clock,
                                   TurnTimerScheduler timers, TurnTimerConfiguration timerConfiguration,
                                   ReconnectGraceScheduler reconnectScheduler,
                                   ReconnectGraceConfiguration reconnectConfiguration,
                                   RoomGameDiscoveryPublisher roomDiscovery) {
        this(runtime, publisher, clock, timers, timerConfiguration, reconnectScheduler,
                reconnectConfiguration, roomDiscovery, (delay,task)->()->{}, () -> Duration.ofSeconds(6));
    }

    public void onLastDisconnect(long userId) {
        runtime.disconnect(userId).ifPresent(transition -> {
            GameRuntimeView view=transition.view();
            synchronized(eventLock(view.gameId())) {
                logTimerState("last-session-disconnect",view.gameId());
                long generation=graceGeneration.incrementAndGet();
                Instant deadline=clock.instant().plus(reconnectConfiguration.grace());
                ReconnectGraceScheduler.Cancellable handle=reconnectScheduler.schedule(deadline,
                        ()->onGraceExpired(view.gameId(),userId,generation));
                ActiveGrace old=activeGrace.put(userId,new ActiveGrace(view.gameId(),generation,deadline,handle));
                if(old!=null)old.handle().cancel();
                publishPublic(view,GameEventType.GAME_STATE_UPDATE,state(view));
            }
        });
    }

    public void onFirstConnection(long userId) {
        ActiveGrace grace=activeGrace.get(userId);
        if(grace==null)return;
        synchronized(eventLock(grace.gameId())) {
            if(activeGrace.get(userId)!=grace||clock.instant().isAfter(grace.deadline()))return;
            runtime.reconnect(userId).ifPresent(transition->{
                if(!activeGrace.remove(userId,grace))return;
                grace.handle().cancel();
                pendingRestorations.put(userId,transition.view());
                publishPublic(transition.view(),GameEventType.GAME_STATE_UPDATE,state(transition.view()));
                if (transition.view().handCompleted() && !transition.view().sessionFinished())
                    scheduleNextHand(transition.view());
            });
        }
    }

    public void onPrivateSubscription(long userId) {
        GameRuntimeView view=pendingRestorations.remove(userId);
        if(view!=null)restorePrivate(view,userId);
    }

    private void onGraceExpired(UUID gameId,long userId,long generation) {
        synchronized(eventLock(gameId)) {
            ActiveGrace grace=activeGrace.get(userId);
            if(grace==null||grace.generation()!=generation||!grace.gameId().equals(gameId))return;
            runtime.expireReconnectWithView(gameId,userId).ifPresent(view -> {
                activeGrace.remove(userId,grace);
                publishPublic(view,GameEventType.GAME_STATE_UPDATE,state(view));
                if (view.sessionFinished()) { cancelHandTransition(gameId); cancelTimer(gameId); }
                else if (view.handCompleted()) scheduleNextHand(view);
            });
            logTimerState("reconnect-grace-expired",gameId);
        }
    }

    private void restorePrivate(GameRuntimeView view,long userId) {
        publishPrivate(view,userId,GameEventType.GAME_STATE_UPDATE,state(view));
        if(view.players().stream().noneMatch(player->player.userId()==userId))return;
        GamePlayerPrivateView own=runtime.privateView(view.gameId(),userId);
        if(!own.holeCards().isEmpty())publishPrivate(view,userId,GameEventType.HOLE_CARDS,
                new HoleCards(view.handId(),own.holeCards()));
        if(Objects.equals(view.currentTurnUserId(),userId)&&!view.handCompleted())publishTurn(view);
        ActiveTimer timer=activeTimers.get(view.gameId());
        if(timer!=null&&timer.handId()==view.handId()&&Objects.equals(timer.turnId(),view.turnId()))
            publishTimerPrivate(view,userId,timer.deadline());
    }

    public GameRuntimeView startGame(long roomId, long requestingUserId) {
        GameRuntimeView view = runtime.startGame(roomId, requestingUserId);
        roomDiscovery.publishStarted(view);
        announceStartedGame(view);
        return view;
    }

    public void announceStartedGame(GameRuntimeView view) {
        synchronized(eventLock(view.gameId())) {
            ensureCompletedLifecycle(view);
            publishPublic(view, GameEventType.GAME_STARTED, new Started(view.gameSessionId(), view.handId(), view.handNumber()));
            publishHandStarted(view);
            if (view.handCompleted()) {
                publishPublic(view, GameEventType.GAME_STATE_UPDATE, state(view));
                publishStoredCompletion(view);
            }
            replaceTimer(view);
        }
    }

    public GameRuntimeView startNextHand(UUID gameId) {
        synchronized(eventLock(gameId)) {
            GameRuntimeView previous = runtime.currentView(gameId);
            GameRuntimeView view = runtime.startNextHand(gameId);
            if (view.sessionFinished()) {
                publishPublic(view, GameEventType.GAME_STATE_UPDATE, state(view));
                cancelHandTransition(view.gameId());
                cancelTimer(view.gameId());
            } else if (isNewHand(previous, view)) {
                ensureCompletedLifecycle(view);
                publishHandStarted(view);
                if (view.handCompleted()) {
                    publishPublic(view, GameEventType.GAME_STATE_UPDATE, state(view));
                    publishStoredCompletion(view);
                }
                replaceTimer(view);
            } else if (view.handCompleted() && !pendingHandTransitions.containsKey(gameId)) {
                scheduleNextHand(view);
            }
            return view;
        }
    }

    public GameRuntimeService.DepartureRequest requestDeparture(UUID gameId, long userId) {
        GameRuntimeService.DepartureRequest departure = runtime.requestDeparture(gameId, userId);
        GameRuntimeView view = departure.view();
        synchronized (eventLock(gameId)) {
            publishPublic(view, GameEventType.GAME_STATE_UPDATE, state(view));
            if (view.sessionFinished()) {
                cancelHandTransition(gameId);
                cancelTimer(gameId);
            } else if (view.handCompleted()) scheduleNextHand(view);
        }
        return departure;
    }

    public void handleAction(UUID gameId, long userId, GameActionMessage message) {
        try {
            if (!runtime.isParticipant(gameId, userId)) throw new GameRuntimeException("NOT_GAME_PARTICIPANT");
            GameActionOutcome outcome = runtime.applyActionWithOutcome(gameId, userId, message.toIntent());
            synchronized(eventLock(gameId)) { publishAccepted(outcome); replaceTimer(outcome.after()); }
        } catch (RuntimeException failure) {
            try { if(runtime.currentView(gameId).failed())cancelTimer(gameId); } catch(RuntimeException ignored) {}
            publishError(gameId, userId, message == null ? null : message.clientActionId(), failure);
        }
    }

    private void publishAccepted(GameActionOutcome outcome) {
        GameRuntimeView after = outcome.after();
        ensureCompletedLifecycle(after);
        publishPublic(after, GameEventType.PLAYER_ACTION, new PlayerAction(outcome.userId(), outcome.seat(),
                outcome.actionType(), outcome.amountCommitted(), outcome.resultingCurrentBet(),
                outcome.resultingTableChips(), outcome.clientActionId(), outcome.automatic()));
        if (after.communityCards().size() > outcome.before().communityCards().size()) {
            publishPublic(after, GameEventType.COMMUNITY_CARDS, new CommunityCards(after.phase(), after.communityCards()));
        }
        publishPublic(after, GameEventType.GAME_STATE_UPDATE, state(after));
        if (after.handCompleted()) publishCompletion(after, outcome.settlement());
        else publishTurn(after);
    }

    private void publishCompletion(GameRuntimeView view, HandSettlementResult settlement) {
        ensureCompletedLifecycle(view);
        String reason = settlement.foldOnly() ? "ALL_OTHERS_FOLDED" : "SHOWDOWN";
        if (!settlement.foldOnly()) publishPublic(view, GameEventType.SHOWDOWN, new Showdown(view.handId(), view.communityCards()));
        List<Award> awards = settlement.potAwards().stream().map(award -> new Award(award.potIndex(),
                award.potType().name(), award.potAmount(), award.winnerUserIds(), award.baseShare(),
                award.oddChipUserIds(), award.winnerPayouts())).toList();
        List<Return> returns = settlement.uncalledBetReturns().stream()
                .map(value -> new Return(value.userId(), value.amount())).toList();
        publishPublic(view, GameEventType.GAME_RESULT, new Result(view.handId(), awards, returns, players(view), reason));
        publishPublic(view, GameEventType.HAND_FINISHED, new HandFinished(view.handId(), view.handNumber(), reason));
    }

    private void publishStoredCompletion(GameRuntimeView view) {
        runtime.completedSettlement(view.gameId(), view.handId()).ifPresentOrElse(
                settlement -> publishCompletion(view, settlement),
                () -> {
                    LOGGER.error("Completed hand has no settlement snapshot game={} hand={}", view.gameId(), view.handId());
                    scheduleNextHand(view);
                });
    }

    private static boolean isNewHand(GameRuntimeView previous, GameRuntimeView current) {
        return previous.handId() != current.handId() || previous.handNumber() != current.handNumber();
    }

    private void ensureCompletedLifecycle(GameRuntimeView view) {
        if (view.handCompleted() && !view.sessionFinished() && !view.failed()) scheduleNextHand(view);
    }

    private void scheduleNextHand(GameRuntimeView completed) {
        if (!completed.handCompleted() || completed.sessionFinished() || completed.failed()) return;
        synchronized (eventLock(completed.gameId())) {
            PendingHandTransition existing = pendingHandTransitions.get(completed.gameId());
            if (existing != null && existing.handId() == completed.handId()
                    && existing.handNumber() == completed.handNumber()) return;
            if (existing != null) existing.handle().cancel();
            long generation = handTransitionGeneration.incrementAndGet();
            DeferredCancellation handle = new DeferredCancellation();
            PendingHandTransition pending = new PendingHandTransition(
                    completed.handId(), completed.handNumber(), generation, handle);
            pendingHandTransitions.put(completed.gameId(), pending);
            try {
                handle.install(handTransitions.schedule(handTransitionConfiguration.interHandDelay(),
                        () -> runNextHand(completed.gameId(), completed.handId(), completed.handNumber(), generation)));
            } catch (RuntimeException failure) {
                pendingHandTransitions.remove(completed.gameId(), pending);
                throw failure;
            }
        }
    }

    private void runNextHand(UUID gameId, long handId, long handNumber, long generation) {
        GameRuntimeView retry = null;
        synchronized (eventLock(gameId)) {
            PendingHandTransition pending = pendingHandTransitions.get(gameId);
            if (pending == null || pending.handId() != handId || pending.handNumber() != handNumber
                    || pending.generation() != generation) return;
            try {
                GameRuntimeView transitioned = startNextHand(gameId);
                if (transitioned.handCompleted() && !transitioned.sessionFinished() && !transitioned.failed()
                        && transitioned.handId() == handId && transitioned.handNumber() == handNumber) retry = transitioned;
            } catch (GameRuntimeException failure) {
                if ("NEXT_HAND_NOT_ALLOWED".equals(failure.code()) || "GAME_NOT_ACTIVE".equals(failure.code()))
                    LOGGER.debug("Stale hand transition ignored game={} hand={} reason={}", gameId, handId, failure.code());
                else {
                    LOGGER.error("Hand transition failed game={} hand={} reason={}", gameId, handId, failure.code(), failure);
                    retry = retryableCompletedView(gameId, handId, handNumber);
                }
            } catch (RuntimeException failure) {
                LOGGER.error("Hand transition failed unexpectedly game={} hand={}", gameId, handId, failure);
                retry = retryableCompletedView(gameId, handId, handNumber);
            } finally {
                pendingHandTransitions.remove(gameId, pending);
            }
        }
        if (retry != null) scheduleNextHand(retry);
    }

    private GameRuntimeView retryableCompletedView(UUID gameId, long handId, long handNumber) {
        try {
            GameRuntimeView current = runtime.currentView(gameId);
            return current.handCompleted() && !current.sessionFinished() && !current.failed()
                    && current.handId() == handId && current.handNumber() == handNumber ? current : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void cancelHandTransition(UUID gameId) {
        PendingHandTransition pending = pendingHandTransitions.remove(gameId);
        if (pending != null) pending.handle().cancel();
    }

    boolean hasPendingHandTransition(UUID gameId) { return pendingHandTransitions.containsKey(gameId); }

    private void publishHandStarted(GameRuntimeView view) {
        publishPublic(view, GameEventType.HAND_STARTED, new HandStarted(view.handId(), view.handNumber(),
                view.dealerSeat(), view.smallBlindSeat(), view.bigBlindSeat(), view.smallBlind(), view.bigBlind(), players(view)));
        for (GameRuntimeView.PlayerView player : view.players()) {
            GamePlayerPrivateView privateView = runtime.privateView(view.gameId(), player.userId());
            publishPrivate(view, player.userId(), GameEventType.HOLE_CARDS,
                    new HoleCards(view.handId(), privateView.holeCards()));
        }
        publishTurn(view);
    }

    private void publishTurn(GameRuntimeView view) {
        if (view.currentTurnUserId() == null || view.handCompleted()) return;
        GamePlayerPrivateView privateView = runtime.privateView(view.gameId(), view.currentTurnUserId());
        var legal = privateView.legalActions();
        publishPrivate(view, privateView.userId(), GameEventType.YOUR_TURN,
                new Turn(view.handId(), view.turnId(), legal.actions(), legal.callAmount(),
                        legal.minimumTarget(), legal.maximumTarget(), privateView.tableChips()));
    }

    private void publishError(UUID gameId, long userId, UUID clientActionId, RuntimeException failure) {
        try {
            GameRuntimeView view = runtime.currentView(gameId);
            String code = failure instanceof BettingRuleViolationException && failure.getMessage() != null
                    && failure.getMessage().toLowerCase().contains("turn") ? "STALE_TURN" : "ACTION_REJECTED";
            publishPrivate(view, userId, GameEventType.COMMAND_ERROR, new CommandError(code, clientActionId));
        } catch (RuntimeException ignored) {
            // No routable game scope exists; the inbound transport already rejects the command safely.
        }
    }

    private State state(GameRuntimeView view) {
        return new State(view.handId(), view.handNumber(), view.phase(), view.dealerSeat(), view.smallBlindSeat(),
                view.bigBlindSeat(), view.currentTurnUserId(), view.currentBet(), view.minimumRaise(),
                view.communityCards(), players(view), view.handCompleted(), view.sessionFinished());
    }
    private List<PublicPlayer> players(GameRuntimeView view) {
        return view.players().stream().map(player -> new PublicPlayer(player.userId(), player.seat(), player.tableChips(),
                player.currentBet(), player.participation(), player.connected(), player.leaving(), player.totalCommitted())).toList();
    }
    private void publishPublic(GameRuntimeView view, GameEventType type, Object payload) {
        publisher.publishPublic(event(view, type, payload));
    }
    private void publishPrivate(GameRuntimeView view, long userId, GameEventType type, Object payload) {
        publisher.publishPrivate(userId, event(view, type, payload));
    }
    private GameRealtimeEvent event(GameRuntimeView view, GameEventType type, Object payload) {
        return new GameRealtimeEvent(UUID.randomUUID(), type, Instant.now(clock), view.roomId(), view.gameId(),
                view.stateVersion(), payload);
    }

    private void replaceTimer(GameRuntimeView view) {
        cancelTimer(view.gameId());
        if(view.failed()||view.handCompleted()||view.sessionFinished()||view.currentTurnUserId()==null||view.turnId()==null)return;
        Instant deadline=clock.instant().plus(timerConfiguration.turnTimeout());
        UUID expectedTurn=view.turnId(); long expectedHand=view.handId();
        TurnTimerScheduler.Cancellable deadlineHandle=timers.schedule(deadline,
                ()->onDeadline(view.gameId(),expectedHand,expectedTurn));
        TurnTimerScheduler.Cancellable tickHandle=timers.scheduleAtFixedRate(timerConfiguration.updateCadence(),
                ()->publishTimerIfCurrent(view.gameId(),expectedHand,expectedTurn,deadline));
        activeTimers.put(view.gameId(),new ActiveTimer(expectedHand,expectedTurn,deadline,deadlineHandle,tickHandle));
        LOGGER.info("Turn deadline scheduled game={} hand={} turn={} actor={} deadline={} delayMs={} cancelled={} done={}",
                view.gameId(),expectedHand,expectedTurn,view.currentTurnUserId(),deadline,
                Math.max(0,Duration.between(clock.instant(),deadline).toMillis()),deadlineHandle.isCancelled(),deadlineHandle.isDone());
        publishTimer(view,deadline);
    }
    private void onDeadline(UUID gameId,long handId,UUID turnId) {
        synchronized(eventLock(gameId)) {
            ActiveTimer active=activeTimers.get(gameId);
            LOGGER.info("Turn deadline callback entered game={} hand={} turn={} activePresent={} activeCancelled={} activeDone={}",
                    gameId,handId,turnId,active!=null,active!=null&&active.deadlineHandle().isCancelled(),
                    active!=null&&active.deadlineHandle().isDone());
            if(active==null){LOGGER.info("Turn deadline ignored reason=NO_ACTIVE_TIMER game={} hand={} turn={}",gameId,handId,turnId);return;}
            if(active.handId()!=handId){LOGGER.info("Turn deadline ignored reason=HAND_MISMATCH game={} expectedHand={} activeHand={}",gameId,handId,active.handId());return;}
            if(!active.turnId().equals(turnId)){LOGGER.info("Turn deadline ignored reason=TURN_MISMATCH game={} expectedTurn={} activeTurn={}",gameId,turnId,active.turnId());return;}
            if(active.deadlineHandle().isCancelled()){LOGGER.info("Turn deadline ignored reason=CANCELLED game={} hand={} turn={}",gameId,handId,turnId);return;}
            try {
                LOGGER.info("Calling authoritative turn timeout game={} hand={} turn={}",gameId,handId,turnId);
                runtime.handleTurnTimeout(gameId,handId,turnId)
                        .ifPresent(outcome->{publishAccepted(outcome);replaceTimer(outcome.after());});
            } catch(RuntimeException failure) {
                LOGGER.error("Authoritative turn timeout failed for game {} hand {} turn {}",gameId,handId,turnId,failure);
                cancelTimer(gameId);
            }
        }
    }
    private void publishTimerIfCurrent(UUID gameId,long handId,UUID turnId,Instant deadline) {
        synchronized(eventLock(gameId)) {
            ActiveTimer active=activeTimers.get(gameId);
            if(active==null||active.handId()!=handId||!active.turnId().equals(turnId))return;
            GameRuntimeView current;
            try {current=runtime.currentView(gameId);} catch(RuntimeException ignored){cancelTimer(gameId);return;}
            if(current.failed()||current.handCompleted()||current.sessionFinished()||current.handId()!=handId
                    ||!Objects.equals(current.turnId(),turnId)){cancelTimer(gameId);return;}
            int seat=current.players().stream().filter(player->player.userId()==current.currentTurnUserId())
                    .mapToInt(GameRuntimeView.PlayerView::seat).findFirst().orElseThrow();
            publishTimer(current,deadline);
        }
    }
    private void publishTimer(GameRuntimeView current,Instant deadline) {
        int seat=current.players().stream().filter(player->player.userId()==current.currentTurnUserId())
                .mapToInt(GameRuntimeView.PlayerView::seat).findFirst().orElseThrow();
        long remaining=Math.max(0,(Duration.between(clock.instant(),deadline).toMillis()+999)/1000);
        publishPublic(current,GameEventType.TIMER_UPDATE,new Timer(current.handId(),current.turnId(),seat,remaining,deadline));
    }
    private void publishTimerPrivate(GameRuntimeView current,long userId,Instant deadline) {
        int seat=current.players().stream().filter(player->player.userId()==current.currentTurnUserId())
                .mapToInt(GameRuntimeView.PlayerView::seat).findFirst().orElseThrow();
        long remaining=Math.max(0,(Duration.between(clock.instant(),deadline).toMillis()+999)/1000);
        publishPrivate(current,userId,GameEventType.TIMER_UPDATE,
                new Timer(current.handId(),current.turnId(),seat,remaining,deadline));
    }

    public TimerSnapshot currentTimer(UUID gameId, long handId, UUID turnId) {
        ActiveTimer timer = activeTimers.get(gameId);
        if (timer == null || timer.handId() != handId || !Objects.equals(timer.turnId(), turnId)) return null;
        long remaining = Math.max(0, (Duration.between(clock.instant(), timer.deadline()).toMillis() + 999) / 1000);
        return new TimerSnapshot(timer.deadline(), remaining);
    }

    public record TimerSnapshot(Instant deadline, long remainingSeconds) {}
    private void cancelTimer(UUID gameId) {
        ActiveTimer old=activeTimers.remove(gameId);if(old!=null){
            LOGGER.info("Turn deadline cancelled game={} hand={} turn={} deadline={} alreadyCancelled={} alreadyDone={}",
                    gameId,old.handId(),old.turnId(),old.deadline(),old.deadlineHandle().isCancelled(),old.deadlineHandle().isDone());
            old.deadlineHandle().cancel();old.tickHandle().cancel();}
    }
    private void logTimerState(String stage,UUID gameId){ActiveTimer timer=activeTimers.get(gameId);
        LOGGER.info("Turn deadline state stage={} game={} present={} hand={} turn={} deadline={} cancelled={} done={}",stage,gameId,
                timer!=null,timer==null?null:timer.handId(),timer==null?null:timer.turnId(),timer==null?null:timer.deadline(),
                timer!=null&&timer.deadlineHandle().isCancelled(),timer!=null&&timer.deadlineHandle().isDone());}
    private Object eventLock(UUID gameId){return eventLocks.computeIfAbsent(gameId,ignored->new Object());}
    private record ActiveTimer(long handId,UUID turnId,Instant deadline,TurnTimerScheduler.Cancellable deadlineHandle,
                               TurnTimerScheduler.Cancellable tickHandle) {}
    private record ActiveGrace(UUID gameId,long generation,Instant deadline,ReconnectGraceScheduler.Cancellable handle) {}
    private record PendingHandTransition(long handId, long handNumber, long generation,
                                         HandTransitionScheduler.Cancellable handle) {}

    private static final class DeferredCancellation implements HandTransitionScheduler.Cancellable {
        private final AtomicReference<HandTransitionScheduler.Cancellable> delegate = new AtomicReference<>();
        private final AtomicBoolean cancellationRequested = new AtomicBoolean();

        void install(HandTransitionScheduler.Cancellable scheduled) {
            if (!delegate.compareAndSet(null, Objects.requireNonNull(scheduled)))
                throw new IllegalStateException("hand transition handle already installed");
            if (cancellationRequested.get()) scheduled.cancel();
        }

        @Override public void cancel() {
            cancellationRequested.set(true);
            HandTransitionScheduler.Cancellable scheduled = delegate.get();
            if (scheduled != null) scheduled.cancel();
        }

        @Override public boolean isCancelled() {
            HandTransitionScheduler.Cancellable scheduled = delegate.get();
            return cancellationRequested.get() || scheduled != null && scheduled.isCancelled();
        }

        @Override public boolean isDone() {
            HandTransitionScheduler.Cancellable scheduled = delegate.get();
            return scheduled != null && scheduled.isDone();
        }
    }
}
