package com.ptit.poker.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ptit.poker.game.api.ActiveGameResponse;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.domain.betting.*;
import com.ptit.poker.game.domain.card.*;
import com.ptit.poker.game.domain.state.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameSnapshotQueryServiceTests {
    private final GameRuntimeService runtime = mock(GameRuntimeService.class);
    private final GameRealtimeApplicationService realtime = mock(GameRealtimeApplicationService.class);
    private final GameObservationQueryService observations = mock(GameObservationQueryService.class);
    private final UUID gameId = UUID.randomUUID(), turnId = UUID.randomUUID();
    private final GameSnapshotQueryService queries = new GameSnapshotQueryService(runtime, realtime, observations);

    @BeforeEach void setUp() { when(runtime.currentView(gameId)).thenReturn(view()); when(runtime.currentViewByRoom(9)).thenReturn(Optional.of(view())); }

    @Test void participantReceivesOnlyOwnCardsAndOwnTurnContract() {
        Card ace = new Card(Rank.ACE, Suit.SPADES);
        when(runtime.canObserve(gameId, 1)).thenReturn(true); when(runtime.isParticipant(gameId, 1)).thenReturn(true);
        when(runtime.privateView(gameId, 1)).thenReturn(new GamePlayerPrivateView(1, 31, gameId, turnId, 7,
                List.of(ace), new LegalActions(EnumSet.of(PokerActionType.CHECK), 0, 20, 900), 900));
        when(realtime.currentTimer(gameId, 31, turnId)).thenReturn(new GameRealtimeApplicationService.TimerSnapshot(Instant.parse("2026-09-01T00:00:30Z"), 30));
        var snapshot = queries.snapshot(gameId, 1);
        assertThat(snapshot.holeCards()).containsExactly(ace);
        assertThat(snapshot.turn().legalActions()).containsExactly(PokerActionType.CHECK);
        assertThat(snapshot.timer().currentTurnSeat()).isEqualTo(1);
        assertThat(snapshot.publicState().players()).extracting(player -> player.totalCommitted()).containsExactly(10L, 20L);
    }

    @Test void spectatorReceivesPublicStateWithoutCardsOrActions() {
        when(runtime.canObserve(gameId, 99)).thenReturn(true); when(runtime.isParticipant(gameId, 99)).thenReturn(false);
        var snapshot = queries.snapshot(gameId, 99);
        assertThat(snapshot.participant()).isFalse(); assertThat(snapshot.holeCards()).isEmpty(); assertThat(snapshot.turn()).isNull();
    }

    @Test void nonObserverCannotDiscoverOrReadGame() {
        assertThat(queries.snapshot(gameId, 99)).isNull(); assertThat(queries.activeGame(9, 99)).isNull();
    }

    @Test void currentParticipantCanDiscoverExactActiveGameWithoutRoomLookup() {
        when(runtime.currentViewByUser(1)).thenReturn(Optional.of(view()));

        ActiveGameResponse active = queries.activeGameForUser(1);

        assertThat(active.roomId()).isEqualTo(9);
        assertThat(active.gameId()).isEqualTo(gameId);
        assertThat(active.gameSessionId()).isEqualTo(22);
        assertThat(active.handId()).isEqualTo(31);
        assertThat(active.handNumber()).isEqualTo(4);
        assertThat(queries.activeGameForUser(99)).isNull();
    }

    @Test void finishedParticipantReceivesTerminalHistoricalSnapshotWithOnlyOwnCards() {
        Card board = new Card(Rank.ACE, Suit.SPADES);
        Card own = new Card(Rank.KING, Suit.HEARTS);
        when(observations.finishedSnapshot(gameId, 1)).thenReturn(Optional.of(new HistoricalGameSnapshot(
                9, gameId, 22, 32, 5, 2, 2, 1, GamePhase.FINISHED, List.of(board), List.of(
                new HistoricalGameSnapshot.Player(1, 1, 1_000, PokerPlayerState.ACTIVE, true, false),
                new HistoricalGameSnapshot.Player(2, 2, 0, PokerPlayerState.ALL_IN, true, false)), List.of(own))));

        var snapshot = queries.snapshot(gameId, 1);

        assertThat(snapshot.version()).isZero();
        assertThat(snapshot.publicState().sessionFinished()).isTrue();
        assertThat(snapshot.publicState().handCompleted()).isTrue();
        assertThat(snapshot.publicState().communityCards()).containsExactly(board);
        assertThat(snapshot.publicState().players()).extracting(player -> player.tableChips()).containsExactly(1_000L, 0L);
        assertThat(snapshot.holeCards()).containsExactly(own);
        assertThat(snapshot.turn()).isNull();
        assertThat(snapshot.timer()).isNull();
        assertThat(snapshot.participant()).isTrue();
    }

    private GameRuntimeView view() { return new GameRuntimeView(gameId, 22, 9, 31, 4, 1, 1, 2, GamePhase.PRE_FLOP, 1L,
            turnId, 7, 20, 20, 10, 20, List.of(), List.of(
            new GameRuntimeView.PlayerView(1, 1, 900, 10, 10, PokerPlayerState.ACTIVE, 2, true, false),
            new GameRuntimeView.PlayerView(2, 2, 880, 20, 20, PokerPlayerState.ACTIVE, 2, true, false)), false, false, false); }
}
