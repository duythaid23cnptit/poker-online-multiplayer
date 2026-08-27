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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service @Profile("!bootstrap")
public class GameRuntimeService {
    private final ActiveGameRegistry registry; private final RoomGamePort rooms;
    private final GameSessionPersistenceService sessions; private final HandHistoryPersistenceService history;
    private final DeckFactory decks; private final PokerRoundEngine rounds = new PokerRoundEngine();
    private final HandSettlementEngine settlements = new HandSettlementEngine(new HandEvaluator());
    public GameRuntimeService(ActiveGameRegistry registry, RoomGamePort rooms,
                              GameSessionPersistenceService sessions, HandHistoryPersistenceService history,
                              DeckFactory decks) {
        this.registry = registry; this.rooms = rooms; this.sessions = sessions; this.history = history; this.decks = decks;
    }

    public GameRuntimeView startGame(long roomId) {
        RoomGamePort.RoomGameSnapshot room = rooms.load(roomId);
        List<RoomGamePort.RoomSeat> eligible = room.seats().stream()
                .filter(s -> s.tableChips() > 0 && (s.state() == RoomPlayerState.READY || s.state() == RoomPlayerState.PLAYING))
                .sorted(Comparator.comparingInt(RoomGamePort.RoomSeat::seatNumber)).toList();
        if (eligible.size() < 2) throw new GameRuntimeException("INSUFFICIENT_PLAYERS");
        UUID gameId = UUID.randomUUID();
        GameSessionView session = sessions.startSession(roomId);
        ActiveGameContext context = new ActiveGameContext(gameId, session.id(), roomId);
        if (!registry.register(context)) { sessions.abortSession(session.id()); throw new GameRuntimeException("GAME_ALREADY_ACTIVE"); }
        try {
            startHand(context, room, eligible, eligible.getFirst().seatNumber());
            rooms.markPlaying(roomId, eligible.stream().map(RoomGamePort.RoomSeat::userId).toList());
            return view(context);
        } catch (RuntimeException failure) {
            registry.remove(context); sessions.abortSession(session.id()); throw failure;
        }
    }

    public GameRuntimeView applyAction(UUID gameId, long userId, GameActionIntent intent) {
        return applyActionWithOutcome(gameId, userId, intent).after();
    }

    public GameActionOutcome applyActionWithOutcome(UUID gameId, long userId, GameActionIntent intent) {
        ActiveGameContext context = registry.require(gameId); context.lock.lock();
        try {
            requireMutable(context);
            GameRuntimeView before = view(context);
            BettingAction action = new BettingAction(userId, intent.turnId(), intent.type(), intent.targetCurrentBet());
            GamePhase phase = context.state.phase();
            try {
                RoundTransitionResult transition = rounds.act(context.state, context.round, action, context.deck);
                PokerPlayer player = context.state.requirePlayer(userId);
                history.recordAcceptedAction(context.history.pokerHandId(), new AcceptedActionHistory(userId, phase,
                        transition.bettingResult().actionType(), transition.bettingResult().amountCommitted(),
                        transition.bettingResult().resultingCurrentBet(), context.state.currentBet(), player.tableChips(),
                        intent.turnId(), intent.clientActionId()));
                HandSettlementResult settlement = null;
                if (transition.handDecidedByFold() || context.state.phase() == GamePhase.SHOWDOWN) settlement = complete(context);
                return new GameActionOutcome(before, view(context), userId, player.seatNumber(),
                        transition.bettingResult().actionType(), transition.bettingResult().amountCommitted(),
                        transition.bettingResult().resultingCurrentBet(), player.tableChips(), intent.clientActionId(), settlement);
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

    public GameRuntimeView startNextHand(UUID gameId) {
        ActiveGameContext context = registry.require(gameId); context.lock.lock();
        try {
            if (!context.handCompleted || context.failed || context.sessionFinished) throw new GameRuntimeException("NEXT_HAND_NOT_ALLOWED");
            List<RoomGamePort.RoomSeat> eligible = context.state.players().stream()
                    .filter(p -> p.tableChips() > 0 && !p.isLeaving())
                    .map(p -> new RoomGamePort.RoomSeat(p.userId(), p.seatNumber(), p.tableChips(),
                            p.isConnected() ? RoomPlayerState.PLAYING : RoomPlayerState.DISCONNECTED)).toList();
            if (eligible.size() < 2) {
                sessions.finishSession(context.sessionId); context.sessionFinished = true; registry.remove(context); return view(context);
            }
            int dealer = nextSeat(eligible, context.state.dealerPosition());
            startHand(context, rooms.load(context.roomId), eligible, dealer);
            return view(context);
        } finally { context.lock.unlock(); }
    }

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
        c.handCompleted = true;
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
                p.holeCards().size(), p.isConnected(), p.isLeaving())).toList(),
                c.handCompleted, c.sessionFinished, c.failed);
    }
}
