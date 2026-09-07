package com.ptit.poker.room.application;

import com.ptit.poker.player.application.RoomPlayerAccountPort;
import com.ptit.poker.player.application.exception.InsufficientAccountChipsException;
import com.ptit.poker.room.api.dto.JoinRoomRequest;
import com.ptit.poker.room.application.exception.RoomBusinessException;
import com.ptit.poker.room.domain.RoomPlayerState;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomApplicationServiceTests {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomPlayerRepository members = mock(RoomPlayerRepository.class);
    private final RoomPlayerAccountPort accounts = mock(RoomPlayerAccountPort.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RoomApplicationService service = new RoomApplicationService(rooms, members, accounts, passwords, events);
    private RoomEntity room;

    @BeforeEach
    void setUp() {
        room = mock(RoomEntity.class);
        when(room.getId()).thenReturn(1L);
        when(room.getRoomType()).thenReturn(RoomType.PRIVATE);
        when(room.getPasswordHash()).thenReturn("hash");
        when(room.getStatus()).thenReturn(RoomStatus.WAITING);
        when(room.getMaxPlayers()).thenReturn(6);
        when(room.getBuyIn()).thenReturn(100L);
        when(room.getName()).thenReturn("Private table");
        when(room.getOwnerUserId()).thenReturn(10L);
        when(rooms.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(members.countByRoomIdAndSeatNumberIsNotNullAndLeftAtIsNull(1L)).thenReturn(0L);
        when(members.findAllByRoomIdAndLeftAtIsNullOrderById(1L)).thenReturn(List.of());
    }

    @Test
    void activeSpectatorConversionReusesMembershipWithoutCheckingPassword() {
        RoomPlayerEntity spectator = new RoomPlayerEntity(1L, 10L, null, RoomPlayerState.SPECTATING, 0);
        when(members.findByRoomIdAndUserIdForUpdate(1L, 10L)).thenReturn(Optional.of(spectator));

        service.join(10L, 1L, new JoinRoomRequest(false, 2, 100L, null));

        verify(passwords, never()).matches(any(), any());
        verify(accounts).debit(10L, 100L);
        verify(members).saveAndFlush(spectator);
        verify(members, never()).save(any(RoomPlayerEntity.class));
    }

    @Test
    void nonMemberMissingOrWrongPrivatePasswordIsRejectedBeforeBuyIn() {
        when(members.findByRoomIdAndUserIdForUpdate(1L, 20L)).thenReturn(Optional.empty());
        when(passwords.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.join(20L, 1L, new JoinRoomRequest(false, 2, 100L, null)))
                .isInstanceOf(RoomBusinessException.class).hasMessage("Invalid room password");
        assertThatThrownBy(() -> service.join(20L, 1L, new JoinRoomRequest(false, 2, 100L, "wrong")))
                .isInstanceOf(RoomBusinessException.class).hasMessage("Invalid room password");
        verify(accounts, never()).debit(anyLong(), anyLong());
    }

    @Test
    void correctPrivatePasswordStillAuthorizesInitialJoin() {
        when(members.findByRoomIdAndUserIdForUpdate(1L, 20L)).thenReturn(Optional.empty());
        when(passwords.matches("correct", "hash")).thenReturn(true);

        service.join(20L, 1L, new JoinRoomRequest(false, 2, 100L, "correct"));

        verify(passwords).matches("correct", "hash");
        verify(accounts).debit(20L, 100L);
        verify(members).saveAndFlush(any(RoomPlayerEntity.class));
    }

    @Test
    void activeSpectatorConversionStillRejectsAnOccupiedSeat() {
        RoomPlayerEntity spectator = new RoomPlayerEntity(1L, 10L, null, RoomPlayerState.SPECTATING, 0);
        when(members.findByRoomIdAndUserIdForUpdate(1L, 10L)).thenReturn(Optional.of(spectator));
        when(members.existsByRoomIdAndSeatNumberAndLeftAtIsNull(1L, 2)).thenReturn(true);

        assertThatThrownBy(() -> service.join(10L, 1L, new JoinRoomRequest(false, 2, 100L, null)))
                .isInstanceOf(RoomBusinessException.class).hasMessage("Seat is occupied");
        verify(accounts, never()).debit(anyLong(), anyLong());
        verify(members, never()).saveAndFlush(any());
    }

    @Test
    void activeSpectatorConversionStillRejectsInsufficientChips() {
        RoomPlayerEntity spectator = new RoomPlayerEntity(1L, 10L, null, RoomPlayerState.SPECTATING, 0);
        when(members.findByRoomIdAndUserIdForUpdate(1L, 10L)).thenReturn(Optional.of(spectator));
        doThrow(new InsufficientAccountChipsException()).when(accounts).debit(10L, 100L);

        assertThatThrownBy(() -> service.join(10L, 1L, new JoinRoomRequest(false, 2, 100L, null)))
                .isInstanceOf(RoomBusinessException.class).hasMessage("Insufficient account chips");
        verify(members, never()).saveAndFlush(any());
    }
}
