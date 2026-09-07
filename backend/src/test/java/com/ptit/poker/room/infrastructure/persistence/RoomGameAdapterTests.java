package com.ptit.poker.room.infrastructure.persistence;

import com.ptit.poker.player.application.RoomPlayerAccountPort;
import com.ptit.poker.room.domain.RoomPlayerState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomGameAdapterTests {
    private static final Instant FINISHED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomPlayerRepository players = mock(RoomPlayerRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RoomPlayerAccountPort accounts = mock(RoomPlayerAccountPort.class);
    private RoomGameAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RoomGameAdapter(rooms, players, events, accounts,
                Clock.fixed(FINISHED_AT, ZoneOffset.UTC));
    }

    @Test
    void finalizingAnActiveMemberCashOutsCurrentStackAndIsIdempotent() {
        RoomPlayerEntity member = new RoomPlayerEntity(7L, 42L, 1, RoomPlayerState.PLAYING, 1_000);
        when(players.findByRoomIdAndUserIdForUpdate(7L, 42L)).thenReturn(Optional.of(member));

        adapter.finalizeActiveGameDeparture(7L, 42L);
        adapter.finalizeActiveGameDeparture(7L, 42L);

        assertThat(member.getLeftAt()).isEqualTo(FINISHED_AT);
        assertThat(member.getSeatNumber()).isNull();
        assertThat(member.getTableChips()).isZero();
        assertThat(member.getPlayerState()).isEqualTo(RoomPlayerState.SPECTATING);
        verify(accounts, times(1)).credit(42L, 1_000);
    }

    @Test
    void finalizingABustedMemberSafelyProcessesAZeroChipCashOut() {
        RoomPlayerEntity member = new RoomPlayerEntity(7L, 99L, 2, RoomPlayerState.PLAYING, 0);
        when(players.findByRoomIdAndUserIdForUpdate(7L, 99L)).thenReturn(Optional.of(member));

        adapter.finalizeActiveGameDeparture(7L, 99L);

        assertThat(member.getLeftAt()).isEqualTo(FINISHED_AT);
        assertThat(member.getSeatNumber()).isNull();
        assertThat(member.getTableChips()).isZero();
        assertThat(member.getPlayerState()).isEqualTo(RoomPlayerState.SPECTATING);
        verify(accounts).credit(99L, 0);
    }
}
