package com.ptit.poker.game.application;

import com.ptit.poker.game.api.ActiveGameResponse;
import com.ptit.poker.game.api.GameSnapshotResponse;
import com.ptit.poker.game.api.realtime.GameEventPayloads.PublicPlayer;
import com.ptit.poker.game.api.realtime.GameEventPayloads.State;
import com.ptit.poker.game.api.realtime.GameEventPayloads.Timer;
import com.ptit.poker.game.api.realtime.GameEventPayloads.Turn;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.runtime.GamePlayerPrivateView;
import com.ptit.poker.game.application.runtime.GameRuntimeService;
import com.ptit.poker.game.application.runtime.GameRuntimeView;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!bootstrap")
public class GameSnapshotQueryService {
    // Persisted history has no runtime state version. A finished snapshot hydrates from zero and cannot receive
    // later gameplay events, so this terminal baseline is deterministic without fabricating a historical version.
    private static final long HISTORICAL_TERMINAL_VERSION = 0L;
    private final GameRuntimeService runtime;
    private final GameRealtimeApplicationService realtime;
    private final GameObservationQueryService observations;

    public GameSnapshotQueryService(GameRuntimeService runtime, GameRealtimeApplicationService realtime,
                                    GameObservationQueryService observations) {
        this.runtime = runtime;
        this.realtime = realtime;
        this.observations = observations;
    }

    public ActiveGameResponse activeGame(long roomId, long userId) {
        GameRuntimeView view = runtime.currentViewByRoom(roomId).orElse(null);
        if (view == null || !runtime.canObserve(view.gameId(), userId)) return null;
        return new ActiveGameResponse(view.roomId(), view.gameId(), view.gameSessionId(), view.handId(), view.handNumber());
    }

    public ActiveGameResponse activeGameForUser(long userId) {
        return runtime.currentViewByUser(userId).map(view -> new ActiveGameResponse(
                view.roomId(), view.gameId(), view.gameSessionId(), view.handId(), view.handNumber())).orElse(null);
    }

    public GameSnapshotResponse snapshot(UUID gameId, long userId) {
        if (!runtime.canObserve(gameId, userId)) {
            return observations.finishedSnapshot(gameId, userId).map(GameSnapshotQueryService::historical).orElse(null);
        }
        GameRuntimeView view = runtime.currentView(gameId);
        boolean participant = runtime.isParticipant(gameId, userId);
        GamePlayerPrivateView own = participant ? runtime.privateView(gameId, userId) : null;
        Turn turn = own != null && view.currentTurnUserId() != null && view.currentTurnUserId() == userId
                && !view.handCompleted()
                ? new Turn(view.handId(), view.turnId(), own.legalActions().actions(), own.legalActions().callAmount(),
                        own.legalActions().minimumTarget(), own.legalActions().maximumTarget(), own.tableChips())
                : null;
        var activeTimer = realtime.currentTimer(gameId, view.handId(), view.turnId());
        Timer timer = activeTimer == null || view.currentTurnUserId() == null ? null : new Timer(
                view.handId(), view.turnId(), seatOf(view, view.currentTurnUserId()),
                activeTimer.remainingSeconds(), activeTimer.deadline());
        return new GameSnapshotResponse(view.roomId(), view.gameId(), view.gameSessionId(), view.stateVersion(),
                state(view), own == null ? List.of() : own.holeCards(), turn, timer, participant);
    }

    private static GameSnapshotResponse historical(HistoricalGameSnapshot snapshot) {
        State state = new State(snapshot.handId(), snapshot.handNumber(), snapshot.phase(), snapshot.dealerSeat(),
                snapshot.smallBlindSeat(), snapshot.bigBlindSeat(), null, 0, 0, snapshot.board(),
                snapshot.players().stream().map(player -> new PublicPlayer(player.userId(), player.seat(),
                        player.tableChips(), 0, player.participation(), player.connected(), player.leaving())).toList(),
                true, true);
        return new GameSnapshotResponse(snapshot.roomId(), snapshot.gameId(), snapshot.gameSessionId(),
                HISTORICAL_TERMINAL_VERSION, state, snapshot.ownHoleCards(), null, null, true);
    }

    private static State state(GameRuntimeView view) {
        return new State(view.handId(), view.handNumber(), view.phase(), view.dealerSeat(), view.smallBlindSeat(),
                view.bigBlindSeat(), view.currentTurnUserId(), view.currentBet(), view.minimumRaise(),
                view.communityCards(), view.players().stream().map(player -> new PublicPlayer(player.userId(),
                        player.seat(), player.tableChips(), player.currentBet(), player.participation(),
                        player.connected(), player.leaving(), player.totalCommitted())).toList(), view.handCompleted(), view.sessionFinished());
    }

    private static int seatOf(GameRuntimeView view, long userId) {
        return view.players().stream().filter(player -> player.userId() == userId)
                .mapToInt(GameRuntimeView.PlayerView::seat).findFirst().orElseThrow();
    }
}
