package com.ptit.poker.game.application.runtime;

import com.ptit.poker.game.application.*;
import com.ptit.poker.game.domain.betting.*;
import com.ptit.poker.game.domain.card.Card;
import com.ptit.poker.game.domain.card.Deck;
import com.ptit.poker.game.domain.hand.HandEvaluator;
import com.ptit.poker.game.domain.round.PokerRoundEngine;
import com.ptit.poker.game.domain.round.RoundTransitionResult;
import com.ptit.poker.game.domain.settlement.HandSettlementEngine;
import com.ptit.poker.game.domain.settlement.HandSettlementResult;
import com.ptit.poker.game.domain.state.*;
import com.ptit.poker.room.domain.RoomPlayerState;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service @Profile("!bootstrap")
public class GameRuntimeService {
    private static final Logger LOGGER=LoggerFactory.getLogger(GameRuntimeService.class);
    private final ActiveGameRegistry registry; private final RoomGamePort rooms;
    private final AdministrativeGameControlPort administrativeControl;
    private final GameSessionPersistenceService sessions; private final HandHistoryPersistenceService history;
    private final DeckFactory decks; private final PokerRoundEngine rounds = new PokerRoundEngine();
    private final HandSettlementEngine settlements = new HandSettlementEngine(new HandEvaluator());
    @Autowired
    public GameRuntimeService(ActiveGameRegistry registry, RoomGamePort rooms,
                              GameSessionPersistenceService sessions, HandHistoryPersistenceService history,
                              DeckFactory decks, AdministrativeGameControlPort administrativeControl) {
        this.registry = registry; this.rooms = rooms; this.sessions = sessions; this.history = history; this.decks = decks;
        this.administrativeControl = administrativeControl;
    }
    GameRuntimeService(ActiveGameRegistry registry, RoomGamePort rooms,GameSessionPersistenceService sessions,
                       HandHistoryPersistenceService history,DeckFactory decks){
        this(registry,rooms,sessions,history,decks,roomId->false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameRuntimeView startGame(long roomId, long requestingUserId) {
        RoomGamePort.RoomGameSnapshot room = rooms.loadForStart(roomId);
        if (room.ownerUserId() != requestingUserId) throw new GameRuntimeException("ROOM_HOST_REQUIRED");
        return startGame(room);
    }

    private GameRuntimeView startGame(RoomGamePort.RoomGameSnapshot room) {
        long roomId = room.roomId();
        if (administrativeControl.hasPendingTerminationForRoom(roomId))
            throw new GameRuntimeException("ADMIN_TERMINATION_REQUESTED");
        if (room.status() != com.ptit.poker.room.domain.RoomStatus.WAITING)
            throw new GameRuntimeException("ROOM_NOT_WAITING");
        List<RoomGamePort.RoomSeat> seated = room.seats().stream()
                .sorted(Comparator.comparingInt(RoomGamePort.RoomSeat::seatNumber)).toList();
        if (seated.size() < 2) throw new GameRuntimeException("INSUFFICIENT_PLAYERS");
        if (seated.stream().anyMatch(seat -> seat.state() != RoomPlayerState.READY))
            throw new GameRuntimeException("PLAYERS_NOT_READY");
        if (seated.stream().anyMatch(seat -> seat.tableChips() <= 0))
            throw new GameRuntimeException("INVALID_PLAYER_STACK");
        List<RoomGamePort.RoomSeat> eligible = seated;
        UUID gameId = UUID.randomUUID();
        GameSessionView session = sessions.startSession(roomId, gameId);
        ActiveGameContext context = new ActiveGameContext(gameId, session.id(), roomId);
        context.sessionMemberUserIds.addAll(eligible.stream().map(RoomGamePort.RoomSeat::userId).toList());
        if (!registry.register(context)) { sessions.abortSession(session.id()); throw new GameRuntimeException("GAME_ALREADY_ACTIVE"); }
        removeRegistryEntryAfterRollback(context);
        try {
            startHand(context, room, eligible, eligible.getFirst().seatNumber());
            rooms.markPlaying(roomId, eligible.stream().map(RoomGamePort.RoomSeat::userId).toList());
            return view(context);
        } catch (RuntimeException failure) {
            registry.remove(context); sessions.abortSession(session.id()); throw failure;
        }
    }

    private void removeRegistryEntryAfterRollback(ActiveGameContext context) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) registry.remove(context);
            }
        });
    }

    public GameRuntimeView applyAction(UUID gameId, long userId, GameActionIntent intent) {
        return applyActionWithOutcome(gameId, userId, intent).after();
    }

    public GameActionOutcome applyActionWithOutcome(UUID gameId, long userId, GameActionIntent intent) {
        return applyActionWithOutcome(gameId,userId,intent,false);
    }

    private GameActionOutcome applyActionWithOutcome(UUID gameId,long userId,GameActionIntent intent,boolean automatic) {
        ActiveGameContext context = registry.require(gameId); context.lock.lock();
        try {
            requireMutable(context);
            if (!automatic && context.departingUserIds.contains(userId))
                throw new GameRuntimeException("PLAYER_REMOVAL_PENDING");
            GameRuntimeView before = view(context);
            BettingAction action = new BettingAction(userId, intent.turnId(), intent.type(), intent.targetCurrentBet());
            GamePhase phase = context.state.phase();
            try {
                RoundTransitionResult transition = automatic
                        ? rounds.actAutomaticAction(context.state,context.round,action,context.deck)
                        : rounds.act(context.state, context.round, action, context.deck);
                PokerPlayer player = context.state.requirePlayer(userId);
                history.recordAcceptedAction(context.history.pokerHandId(), new AcceptedActionHistory(userId, phase,
                        transition.bettingResult().actionType(), transition.bettingResult().amountCommitted(),
                        transition.bettingResult().resultingCurrentBet(), context.state.currentBet(), player.tableChips(),
                        intent.turnId(), intent.clientActionId()));
                HandSettlementResult settlement = null;
                if (transition.handDecidedByFold() || context.state.phase() == GamePhase.SHOWDOWN) settlement = complete(context);
                return new GameActionOutcome(before, view(context), userId, player.seatNumber(),
                        transition.bettingResult().actionType(), transition.bettingResult().amountCommitted(),
                        transition.bettingResult().resultingCurrentBet(), player.tableChips(), intent.clientActionId(), settlement,
                        intent.clientActionId() == null);
            } catch (RuntimeException failure) {
                if (!(failure instanceof BettingRuleViolationException)) context.failed = true;
                throw failure;
            }
        } finally { context.lock.unlock(); }
    }

    public GamePlayerPrivateView privateView(UUID gameId, long userId) {
        ActiveGameContext context = registry.require(gameId); context.lock.lock();
        try {
            PokerPlayer player = context.state.requirePlayer(userId);
            LegalActions legal = context.handCompleted || context.state.currentTurnUserId() == null
                    ? LegalActions.none() : rounds.legalActions(context.state, context.round, userId);
            return new GamePlayerPrivateView(userId, context.history.pokerHandId(), gameId, context.state.turnId(),
                    context.state.stateVersion(), player.holeCards(), legal, player.tableChips());
        } finally { context.lock.unlock(); }
    }

    public boolean canObserve(UUID gameId, long userId) {
        ActiveGameContext context;
        try { context = registry.require(gameId); } catch (GameRuntimeException ignored) { return false; }
        context.lock.lock();
        try {
            if (context.state.players().stream().anyMatch(player -> player.userId() == userId)) return true;
            return rooms.canObserve(context.roomId, userId);
        } finally { context.lock.unlock(); }
    }

    public boolean isParticipant(UUID gameId, long userId) {
        ActiveGameContext context;
        try { context = registry.require(gameId); } catch (GameRuntimeException ignored) { return false; }
        context.lock.lock();
        try { return context.state.players().stream().anyMatch(player -> player.userId() == userId); }
        finally { context.lock.unlock(); }
    }

    public GameRuntimeView currentView(UUID gameId) {
        ActiveGameContext context = registry.require(gameId); context.lock.lock();
        try { return view(context); } finally { context.lock.unlock(); }
    }

    public Optional<HandSettlementResult> completedSettlement(UUID gameId, long handId) {
        ActiveGameContext context = registry.require(gameId); context.lock.lock();
        try {
            if (!context.handCompleted || context.history.pokerHandId() != handId) return Optional.empty();
            return Optional.ofNullable(context.completedSettlement);
        } finally { context.lock.unlock(); }
    }

    public Optional<GameRuntimeView> currentViewByRoom(long roomId) {
        return registry.findByRoom(roomId).map(context -> {
            context.lock.lock();
            try { return view(context); } finally { context.lock.unlock(); }
        });
    }

    public Optional<GameRuntimeView> currentViewByUser(long userId) {
        return registry.findByUser(userId).flatMap(context -> {
            context.lock.lock();
            try { return context.sessionFinished ? Optional.empty() : Optional.of(view(context)); }
            finally { context.lock.unlock(); }
        });
    }

    public Optional<GameConnectionTransition> disconnect(long userId) {
        Optional<ActiveGameContext> found = registry.findByUser(userId);
        if (found.isEmpty()) return Optional.empty();
        ActiveGameContext context = found.get(); context.lock.lock();
        try {
            PokerPlayer player = context.state.players().stream().filter(value->value.userId()==userId).findFirst().orElse(null);
            if (player != null && !player.isConnected()) return Optional.empty();
            if (!context.departingUserIds.contains(userId)) rooms.markDisconnected(context.roomId, userId);
            if(player!=null){player.markDisconnected();context.state.advanceStateVersion();}
            return Optional.of(new GameConnectionTransition(view(context), userId));
        } finally { context.lock.unlock(); }
    }

    public Optional<GameConnectionTransition> reconnect(long userId) {
        Optional<ActiveGameContext> found = registry.findByUser(userId);
        if (found.isEmpty()) return Optional.empty();
        ActiveGameContext context = found.get(); context.lock.lock();
        try {
            PokerPlayer player = context.state.players().stream().filter(value->value.userId()==userId).findFirst().orElse(null);
            if ((player != null && player.isConnected()) || context.reconnectExpiredUsers.contains(userId)
                    || context.departingUserIds.contains(userId)) return Optional.empty();
            rooms.markReconnected(context.roomId, userId);
            if(player!=null){player.markConnected();context.state.advanceStateVersion();}
            return Optional.of(new GameConnectionTransition(view(context), userId));
        } finally { context.lock.unlock(); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireReconnect(UUID gameId, long userId) {
        return expireReconnectWithView(gameId, userId).isPresent();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<GameRuntimeView> expireReconnectWithView(UUID gameId, long userId) {
        ActiveGameContext context;
        try { context = registry.require(gameId); } catch (GameRuntimeException ignored) { return Optional.empty(); }
        context.lock.lock();
        try {
            PokerPlayer player = context.state.players().stream().filter(value->value.userId()==userId).findFirst().orElse(null);
            if (player != null && player.isConnected()) return Optional.empty();
            boolean added=context.reconnectExpiredUsers.add(userId);
            if(added&&context.handCompleted)finalizeExpiredAndMaybeFinish(context);
            return added ? Optional.of(view(context)) : Optional.empty();
        } finally { context.lock.unlock(); }
    }

    public Optional<GameActionOutcome> handleTurnTimeout(UUID gameId, long expectedHandId, UUID expectedTurnId) {
        LOGGER.info("Authoritative turn timeout entered game={} expectedHand={} expectedTurn={}",gameId,expectedHandId,expectedTurnId);
        ActiveGameContext context;
        try { context=registry.require(gameId); } catch(GameRuntimeException ignored){
            LOGGER.info("Authoritative turn timeout ignored reason=GAME_NOT_FOUND game={} hand={} turn={}",gameId,expectedHandId,expectedTurnId);
            return Optional.empty();}
        context.lock.lock();
        try {
            if(context.failed){LOGGER.info("Authoritative turn timeout ignored reason=RUNTIME_FAILED game={}",gameId);return Optional.empty();}
            if(context.handCompleted){LOGGER.info("Authoritative turn timeout ignored reason=HAND_COMPLETED game={}",gameId);return Optional.empty();}
            if(context.sessionFinished){LOGGER.info("Authoritative turn timeout ignored reason=SESSION_FINISHED game={}",gameId);return Optional.empty();}
            if(context.history.pokerHandId()!=expectedHandId){LOGGER.info("Authoritative turn timeout ignored reason=HAND_MISMATCH game={}",gameId);return Optional.empty();}
            if(context.state.currentTurnUserId()==null){LOGGER.info("Authoritative turn timeout ignored reason=NO_CURRENT_ACTOR game={}",gameId);return Optional.empty();}
            if(!Objects.equals(context.state.turnId(),expectedTurnId)){LOGGER.info("Authoritative turn timeout ignored reason=TURN_MISMATCH game={}",gameId);return Optional.empty();}
            long actor=context.state.currentTurnUserId();
            LegalActions legal=rounds.legalActionsForAutomaticAction(context.state,context.round,actor);
            if(legal.actions().isEmpty()){LOGGER.info("Authoritative turn timeout ignored reason=NO_AUTOMATIC_ACTION game={} actor={}",gameId,actor);return Optional.empty();}
            PokerActionType type=legal.allows(PokerActionType.CHECK)?PokerActionType.CHECK:PokerActionType.FOLD;
            return Optional.of(applyActionWithOutcome(gameId,actor,new GameActionIntent(expectedTurnId,null,type,0),true));
        } finally {context.lock.unlock();}
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GameRuntimeView startNextHand(UUID gameId) {
        ActiveGameContext context = registry.require(gameId); context.lock.lock();
        try {
            if (!context.handCompleted || context.failed || context.sessionFinished) throw new GameRuntimeException("NEXT_HAND_NOT_ALLOWED");
            markRuntimeFailedAfterRollback(context);
            finalizeDeferredDepartures(context);
            if (context.administrativeTerminationRequested) {
                finishAdministrativeTermination(context);
                return view(context);
            }
            RoomGamePort.RoomGameSnapshot room=rooms.load(context.roomId);
            List<RoomGamePort.RoomSeat> sessionSeats=room.seats().stream()
                    .filter(seat->context.sessionMemberUserIds.contains(seat.userId()))
                    .map(seat->withAuthoritativeStack(context,seat)).toList();
            List<RoomGamePort.RoomSeat> eligible=sessionSeats.stream()
                    .filter(GameRuntimeService::eligibleForNewHand).toList();
            if (eligible.size() < 2) {
                boolean liveGrace=sessionSeats.stream().anyMatch(seat->seat.state()==RoomPlayerState.DISCONNECTED
                        &&!context.reconnectExpiredUsers.contains(seat.userId()));
                if(liveGrace)return view(context);
                finishSessionAndRoom(context);
                return view(context);
            }
            int dealer = nextSeat(eligible, context.state.dealerPosition());
            startHand(context, room, eligible, dealer);
            return view(context);
        } catch (RuntimeException failure) {
            if (!(failure instanceof GameRuntimeException runtimeFailure
                    && "NEXT_HAND_NOT_ALLOWED".equals(runtimeFailure.code()))) context.failed = true;
            throw failure;
        } finally { context.lock.unlock(); }
    }

    private void markRuntimeFailedAfterRollback(ActiveGameContext context) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) return;
                context.lock.lock();
                try { context.failed = true; } finally { context.lock.unlock(); }
            }
        });
    }

    private void finalizeExpiredAndMaybeFinish(ActiveGameContext context) {
        finalizeExpiredDepartures(context);
        RoomGamePort.RoomGameSnapshot room=rooms.load(context.roomId);
        long continuing=room.seats().stream().filter(seat->context.sessionMemberUserIds.contains(seat.userId()))
                .filter(GameRuntimeService::eligibleForNewHand).count();
        boolean liveGrace=room.seats().stream().filter(seat->context.sessionMemberUserIds.contains(seat.userId()))
                .anyMatch(seat->seat.state()==RoomPlayerState.DISCONNECTED
                        &&!context.reconnectExpiredUsers.contains(seat.userId()));
        if(continuing<2&&!liveGrace&&!context.sessionFinished){
            finishSessionAndRoom(context);
        }
    }

    private void finalizeExpiredDepartures(ActiveGameContext context) {
        List<Long> expired=context.reconnectExpiredUsers.stream()
                .filter(context.sessionMemberUserIds::contains).toList();
        for(long userId:expired){rooms.finalizeActiveGameDeparture(context.roomId,userId);context.sessionMemberUserIds.remove(userId);}
    }

    private void finalizeDeferredDepartures(ActiveGameContext context) {
        finalizeExpiredDepartures(context);
        for (long userId : List.copyOf(context.departingUserIds)) {
            rooms.finalizeActiveGameDeparture(context.roomId, userId);
            context.sessionMemberUserIds.remove(userId);
            context.departingUserIds.remove(userId);
        }
    }

    public boolean requestAdministrativeRemoval(long roomId, long userId) {
        ActiveGameContext context = registry.findByRoom(roomId)
                .orElseThrow(() -> new GameRuntimeException("GAME_NOT_ACTIVE"));
        return requestDeparture(context, userId).changed();
    }

    public DepartureRequest requestDeparture(UUID gameId, long userId) {
        return requestDeparture(registry.require(gameId), userId);
    }

    private DepartureRequest requestDeparture(ActiveGameContext context, long userId) {
        context.lock.lock();
        try {
            if (context.sessionFinished) throw new GameRuntimeException("GAME_NOT_ACTIVE");
            if (!context.sessionMemberUserIds.contains(userId)) throw new GameRuntimeException("NOT_GAME_PARTICIPANT");
            if (!context.departingUserIds.add(userId)) {
                return new DepartureRequest(false, !context.handCompleted, view(context));
            }
            rooms.markLeaving(context.roomId, userId);
            context.state.advanceStateVersion();
            if (context.handCompleted) finalizeDeferredDepartures(context);
            return new DepartureRequest(true, !context.handCompleted, view(context));
        } finally { context.lock.unlock(); }
    }

    public AdministrativeTermination requestAdministrativeTermination(long sessionId) {
        ActiveGameContext context = registry.findBySession(sessionId)
                .orElseThrow(() -> new GameRuntimeException("GAME_NOT_ACTIVE"));
        context.lock.lock();
        try {
            if (context.sessionFinished || context.administrativeTerminationRequested)
                return new AdministrativeTermination(false, false);
            context.administrativeTerminationRequested = true;
            if (context.handCompleted) {
                finishAdministrativeTermination(context);
                return new AdministrativeTermination(true, false);
            }
            return new AdministrativeTermination(true, true);
        } finally { context.lock.unlock(); }
    }

    private void finishAdministrativeTermination(ActiveGameContext context) {
        finishSessionAndRoom(context);
    }

    private void finishSessionAndRoom(ActiveGameContext context) {
        if (context.sessionFinished) return;
        finalizeDeferredDepartures(context);
        for (long userId : List.copyOf(context.sessionMemberUserIds)) {
            rooms.finalizeActiveGameDeparture(context.roomId, userId);
            context.sessionMemberUserIds.remove(userId);
        }
        sessions.finishSession(context.sessionId);
        rooms.finishRoom(context.roomId);
        context.sessionFinished = true;
        removeRegistryAfterCommitOrNow(context);
    }

    private void removeRegistryAfterCommitOrNow(ActiveGameContext context) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            registry.remove(context);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { registry.remove(context); }
        });
    }

    public record AdministrativeTermination(boolean changed, boolean deferred) {}
    public record DepartureRequest(boolean changed, boolean deferred, GameRuntimeView view) {}

    private static boolean eligibleForNewHand(RoomGamePort.RoomSeat seat) {
        return seat.tableChips()>0&&(seat.state()==RoomPlayerState.PLAYING||seat.state()==RoomPlayerState.READY);
    }

    private static RoomGamePort.RoomSeat withAuthoritativeStack(ActiveGameContext context,RoomGamePort.RoomSeat seat) {
        return context.state.players().stream().filter(player->player.userId()==seat.userId()).findFirst()
                .map(player->new RoomGamePort.RoomSeat(seat.userId(),seat.seatNumber(),player.tableChips(),seat.state()))
                .orElse(seat);
    }

    public record GameConnectionTransition(GameRuntimeView view, long userId) {}

    private void startHand(ActiveGameContext c, RoomGamePort.RoomGameSnapshot room,
                           List<RoomGamePort.RoomSeat> seats, int dealer) {
        int sb = seats.size() == 2 ? dealer : nextSeat(seats, dealer); int bb = nextSeat(seats, sb);
        List<PokerPlayer> players = seats.stream().map(s -> new PokerPlayer(s.userId(), s.seatNumber(), s.tableChips(),
                0, 0, PokerPlayerState.ACTIVE, List.of(), s.state() != RoomPlayerState.DISCONNECTED, false)).toList();
        postBlind(players, sb, room.smallBlind()); postBlind(players, bb, room.bigBlind());
        Deck deck = decks.create(); deck.shuffle();
        List<RoomGamePort.RoomSeat> dealOrder = clockwise(seats, dealer);
        Map<Long,List<Card>> cards = new HashMap<>(); seats.forEach(s -> cards.put(s.userId(), new ArrayList<>()));
        for (int round = 0; round < 2; round++) for (var seat : dealOrder) cards.get(seat.userId()).add(deck.draw());
        players.forEach(player -> player.dealHoleCards(cards.get(player.userId())));
        long currentBet = players.stream().mapToLong(PokerPlayer::currentBet).max().orElse(0);
        GameState state = new GameState(c.gameId, UUID.randomUUID(), GamePhase.PRE_FLOP, dealer, sb, bb,
                null, currentBet, room.bigBlind(), List.of(), players, Duration.ZERO, 0, null, room.bigBlind());
        c.handNumber++; c.smallBlind = room.smallBlind(); c.bigBlind = room.bigBlind(); c.state = state; c.deck = deck;
        c.completedSettlement = null;
        boolean automaticRunout = players.stream().filter(PokerPlayer::canReceiveBettingTurn).count() <= 1;
        if (automaticRunout) {
            c.history = history.startHand(c.sessionId, c.handNumber, room.smallBlind(), room.bigBlind(), state);
            c.round = rounds.start(state, deck);
        } else {
            c.round = rounds.start(state, deck);
            c.history = history.startHand(c.sessionId, c.handNumber, room.smallBlind(), room.bigBlind(), state);
        }
        c.handCompleted = false;
        if (state.phase() == GamePhase.SHOWDOWN || state.phase() == GamePhase.FINISHED) complete(c);
    }

    private HandSettlementResult complete(ActiveGameContext c) {
        HandSettlementResult result = c.state.phase() == GamePhase.FINISHED
                ? settlements.settleByFolds(c.state) : settlements.settleShowdown(c.state);
        history.completeHand(c.history, c.state, result,
                result.foldOnly() ? HandCompletionReason.ALL_OTHERS_FOLDED : HandCompletionReason.SHOWDOWN);
        rooms.synchronizeTableChips(c.roomId, c.state.players().stream()
                .map(p -> new RoomGamePort.PlayerStack(p.userId(), p.tableChips())).toList());
        c.completedSettlement = result;
        c.handCompleted = true;
        finalizeDeferredDepartures(c);
        if (c.administrativeTerminationRequested) finishAdministrativeTermination(c);
        return result;
    }

    private static void postBlind(List<PokerPlayer> players, int seat, long blind) {
        PokerPlayer p = players.stream().filter(x -> x.seatNumber() == seat).findFirst().orElseThrow();
        p.commitChips(Math.min(blind, p.tableChips()));
    }
    private static int nextSeat(List<RoomGamePort.RoomSeat> seats, int seat) {
        return seats.stream().filter(s -> s.seatNumber() > seat).mapToInt(RoomGamePort.RoomSeat::seatNumber)
                .findFirst().orElse(seats.getFirst().seatNumber());
    }
    private static List<RoomGamePort.RoomSeat> clockwise(List<RoomGamePort.RoomSeat> seats, int dealer) {
        List<RoomGamePort.RoomSeat> result = new ArrayList<>();
        result.addAll(seats.stream().filter(s -> s.seatNumber() > dealer).toList());
        result.addAll(seats.stream().filter(s -> s.seatNumber() <= dealer).toList()); return result;
    }
    private static void requireMutable(ActiveGameContext c) {
        if (c.failed) throw new GameRuntimeException("GAME_REQUIRES_RECOVERY");
        if (c.handCompleted || c.sessionFinished) throw new GameRuntimeException("HAND_NOT_ACTIVE");
    }
    private static GameRuntimeView view(ActiveGameContext c) {
        return new GameRuntimeView(c.gameId, c.sessionId, c.roomId, c.history.pokerHandId(), c.handNumber, c.state.dealerPosition(),
                c.state.smallBlindPosition(), c.state.bigBlindPosition(), c.state.phase(), c.state.currentTurnUserId(),
                c.state.turnId(), c.state.stateVersion(), c.state.currentBet(), c.state.minimumRaise(),
                c.smallBlind, c.bigBlind, c.state.communityCards(),
                c.state.players().stream().map(p -> new GameRuntimeView.PlayerView(p.userId(),
                p.seatNumber(), p.tableChips(), p.currentBet(), p.totalCommitted(), p.playerState(),
                p.holeCards().size(), p.isConnected(), p.isLeaving() || c.departingUserIds.contains(p.userId()))).toList(),
                c.handCompleted, c.sessionFinished, c.failed);
    }
}
