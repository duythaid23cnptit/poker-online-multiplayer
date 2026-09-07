package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.game.domain.state.GamePhase;
import com.ptit.poker.game.domain.state.PokerPlayerState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaHistoricalGameSnapshotAdapterTests {
    private final GameSessionRepository sessions = mock(GameSessionRepository.class);
    private final PokerHandRepository hands = mock(PokerHandRepository.class);
    private final HandPlayerRepository players = mock(HandPlayerRepository.class);
    private final JpaHistoricalGameSnapshotAdapter adapter = new JpaHistoricalGameSnapshotAdapter(sessions, hands, players);
    private final UUID gameId = UUID.randomUUID();

    @Test
    void returnsLastCompletedHandAndOnlyRequestingPlayersPrivateCards() {
        GameSessionEntity session = new GameSessionEntity(9L, gameId, GameSessionStatus.FINISHED,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
        ReflectionTestUtils.setField(session, "id", 22L);
        PokerHandEntity finalHand = new PokerHandEntity(22L, 3, 2, 2, 1, 5, 10,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), GamePhase.FINISHED, "AS,KD,2C", HandEndReason.SHOWDOWN);
        ReflectionTestUtils.setField(finalHand, "id", 33L);
        HandPlayerEntity requester = new HandPlayerEntity(33L, 7L, 1, 455, 1_000, 455,
                PokerPlayerState.ACTIVE, true, false, "QH,QS");
        HandPlayerEntity opponent = new HandPlayerEntity(33L, 8L, 2, 545, 0, 455,
                PokerPlayerState.ALL_IN, true, false, "AH,AD");
        when(sessions.findByGameId(gameId.toString())).thenReturn(Optional.of(session));
        when(players.countByGameSessionIdAndUserId(22L, 7L)).thenReturn(1L);
        when(hands.findFirstByGameSessionIdAndEndReasonIsNotNullOrderByHandNumberDesc(22L))
                .thenReturn(Optional.of(finalHand));
        when(players.findAllByPokerHandIdOrderBySeatNumber(33L)).thenReturn(List.of(requester, opponent));

        var snapshot = adapter.findFinishedForParticipant(gameId, 7L).orElseThrow();

        assertThat(snapshot.handNumber()).isEqualTo(3);
        assertThat(snapshot.board()).hasSize(3);
        assertThat(snapshot.players()).extracting(value -> value.tableChips()).containsExactly(1_000L, 0L);
        assertThat(snapshot.ownHoleCards()).hasSize(2);
        assertThat(snapshot.ownHoleCards()).noneMatch(card -> card.rank().name().equals("ACE"));
    }

    @Test
    void rejectsOutsiderEvenWhenFinishedGameIdExists() {
        GameSessionEntity session = new GameSessionEntity(9L, gameId, GameSessionStatus.FINISHED,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
        ReflectionTestUtils.setField(session, "id", 22L);
        when(sessions.findByGameId(gameId.toString())).thenReturn(Optional.of(session));

        assertThat(adapter.findFinishedForParticipant(gameId, 99L)).isEmpty();
        assertThat(adapter.isFinishedParticipant(gameId, 99L)).isFalse();
    }

    @Test
    void activeOrUnknownSessionCannotBeUsedAsHistoricalAuthorization() {
        GameSessionEntity active = new GameSessionEntity(9L, gameId, GameSessionStatus.ACTIVE, Instant.EPOCH, null);
        when(sessions.findByGameId(gameId.toString())).thenReturn(Optional.of(active));

        assertThat(adapter.findFinishedForParticipant(gameId, 7L)).isEmpty();
        assertThat(adapter.isFinishedParticipant(gameId, 7L)).isFalse();
    }
}
