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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service @Profile("!bootstrap")
public class GameRealtimeApplicationService {
    private final GameRuntimeService runtime;
    private final GameRealtimePublisher publisher;
    private final Clock clock;
    private final TurnTimerScheduler timers;
    private final TurnTimerConfiguration timerConfiguration;
    private final ConcurrentHashMap<UUID, ActiveTimer> activeTimers=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> eventLocks=new ConcurrentHashMap<>();

    public GameRealtimeApplicationService(GameRuntimeService runtime, GameRealtimePublisher publisher, Clock clock,
                                          TurnTimerScheduler timers, TurnTimerConfiguration timerConfiguration) {
        this.runtime = runtime; this.publisher = publisher; this.clock = clock;
        this.timers=timers; this.timerConfiguration=timerConfiguration;
    }

    public GameRuntimeView startGame(long roomId) {
        GameRuntimeView view = runtime.startGame(roomId);
        announceStartedGame(view);
        return view;
    }

    public void announceStartedGame(GameRuntimeView view) {
        synchronized(eventLock(view.gameId())) {
            publishPublic(view, GameEventType.GAME_STARTED, new Started(view.gameSessionId(), view.handId(), view.handNumber()));
            publishHandStarted(view); replaceTimer(view);
        }
    }

    public GameRuntimeView startNextHand(UUID gameId) {
        GameRuntimeView view = runtime.startNextHand(gameId);
        if (view.sessionFinished()) {
            publishPublic(view, GameEventType.GAME_STATE_UPDATE, state(view));
            cancelTimer(view.gameId());
        } else {
            synchronized(eventLock(view.gameId())) { publishHandStarted(view); replaceTimer(view); }
        }
        return view;
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
                player.currentBet(), player.participation(), player.connected(), player.leaving())).toList();
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
        publishTimer(view,deadline);
    }
    private void onDeadline(UUID gameId,long handId,UUID turnId) {
        synchronized(eventLock(gameId)) {
            ActiveTimer active=activeTimers.get(gameId);
            if(active==null||active.handId()!=handId||!active.turnId().equals(turnId))return;
            try {
                runtime.handleTurnTimeout(gameId,handId,turnId)
                        .ifPresent(outcome->{publishAccepted(outcome);replaceTimer(outcome.after());});
            } catch(RuntimeException failure) { cancelTimer(gameId); }
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
    private void cancelTimer(UUID gameId) {
        ActiveTimer old=activeTimers.remove(gameId);if(old!=null){old.deadlineHandle().cancel();old.tickHandle().cancel();}
    }
    private Object eventLock(UUID gameId){return eventLocks.computeIfAbsent(gameId,ignored->new Object());}
    private record ActiveTimer(long handId,UUID turnId,Instant deadline,TurnTimerScheduler.Cancellable deadlineHandle,
                               TurnTimerScheduler.Cancellable tickHandle) {}
}
