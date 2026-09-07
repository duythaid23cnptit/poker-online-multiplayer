package com.ptit.poker.room.application;

import com.ptit.poker.player.application.RoomPlayerAccountPort;
import com.ptit.poker.player.application.exception.InsufficientAccountChipsException;
import com.ptit.poker.room.api.dto.*;
import com.ptit.poker.room.api.event.RoomEventType;
import com.ptit.poker.room.application.exception.RoomBusinessException;
import com.ptit.poker.room.domain.*;
import com.ptit.poker.room.infrastructure.persistence.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@Profile("!bootstrap")
public class RoomApplicationService {
    private final RoomRepository rooms;
    private final RoomPlayerRepository members;
    private final RoomPlayerAccountPort accounts;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final Clock clock = Clock.systemUTC();

    public RoomApplicationService(RoomRepository rooms, RoomPlayerRepository members, RoomPlayerAccountPort accounts,
                                  PasswordEncoder passwords, ApplicationEventPublisher events) {
        this.rooms = rooms; this.members = members; this.accounts = accounts;
        this.passwords = passwords; this.events = events;
    }

    @Transactional
    public RoomDetailResponse create(Long userId, CreateRoomRequest request) {
        try { accounts.requireActive(userId); }
        catch (IllegalStateException ex) { throw forbidden("ACCOUNT_NOT_ACTIVE", "Account is unavailable"); }
        if (request.bigBlind() <= request.smallBlind()) bad("INVALID_BLINDS", "Big blind must exceed small blind");
        String passwordHash = null;
        if (request.roomType() == RoomType.PRIVATE) {
            if (request.password() == null || request.password().isBlank()) bad("ROOM_PASSWORD_REQUIRED", "Private room password is required");
            passwordHash = passwords.encode(request.password());
        } else if (request.password() != null && !request.password().isBlank()) {
            bad("PUBLIC_ROOM_PASSWORD", "Public rooms cannot have a password");
        }
        RoomEntity room = rooms.saveAndFlush(new RoomEntity(request.name().trim(), userId, request.roomType(),
                passwordHash, request.maxPlayers(), request.smallBlind(), request.bigBlind(), request.buyIn(),
                RoomStatus.WAITING, clock.instant()));
        members.save(new RoomPlayerEntity(room.getId(), userId, null, RoomPlayerState.SPECTATING, 0));
        RoomDetailResponse result = snapshot(room.getId());
        events.publishEvent(new RoomChangedEvent(room.getId(), null, null,
                RoomEventType.ROOM_CREATED, result.room()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> list() {
        return rooms.findAllByStatusOrderByLastActivityAtDesc(RoomStatus.WAITING).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public RoomDetailResponse detail(Long userId, Long roomId) {
        RoomEntity requestedRoom = room(roomId);
        if (requestedRoom.getStatus() != RoomStatus.WAITING && !isActiveMember(roomId, userId))
            throw forbidden("ROOM_DETAIL_FORBIDDEN", "Active membership required");
        return snapshot(roomId);
    }

    private RoomDetailResponse snapshot(Long roomId) {
        RoomEntity room = room(roomId);
        List<RoomPlayerResponse> result = members.findAllByRoomIdAndLeftAtIsNullOrderById(roomId).stream()
                .map(member -> new RoomPlayerResponse(member.getUserId(), accounts.username(member.getUserId()), member.getSeatNumber(),
                        member.getPlayerState(), member.getTableChips())).toList();
        return new RoomDetailResponse(summary(room), result);
    }

    @Transactional
    public RoomDetailResponse join(Long userId, Long roomId, JoinRoomRequest request) {
        try { accounts.requireActive(userId); }
        catch (IllegalStateException ex) { throw forbidden("ACCOUNT_NOT_ACTIVE", "Account is unavailable"); }
        RoomEntity room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room not found"));
        if (room.getStatus() != RoomStatus.WAITING) throw conflict("ROOM_NOT_JOINABLE", "Room is not joinable");
        RoomPlayerEntity member = members.findByRoomIdAndUserIdForUpdate(roomId, userId).orElse(null);
        boolean convertsActiveSpectator = member != null && member.isActive()
                && member.getSeatNumber() == null && !request.joinsAsSpectator();
        if (room.getRoomType() == RoomType.PRIVATE && !convertsActiveSpectator
                && (request.password() == null || !passwords.matches(request.password(), room.getPasswordHash())))
            throw forbidden("INVALID_ROOM_PASSWORD", "Invalid room password");
        if (member != null && member.isActive() && !(member.getSeatNumber() == null && !request.joinsAsSpectator()))
            throw conflict("ALREADY_JOINED", "User already joined this room");
        Integer seat = request.joinsAsSpectator() ? null : request.seatNumber();
        long amount = request.joinsAsSpectator() ? 0 : request.buyInAmount() == null ? room.getBuyIn() : request.buyInAmount();
        if (seat == null && !request.joinsAsSpectator()) bad("SEAT_REQUIRED", "A seated player must choose a seat");
        if (seat != null && (seat < 1 || seat > room.getMaxPlayers())) bad("INVALID_SEAT", "Seat is outside room capacity");
        if (seat != null && members.existsByRoomIdAndSeatNumberAndLeftAtIsNull(roomId, seat)) throw conflict("SEAT_OCCUPIED", "Seat is occupied");
        if (seat != null && members.countByRoomIdAndSeatNumberIsNotNullAndLeftAtIsNull(roomId) >= room.getMaxPlayers()) throw conflict("ROOM_FULL", "Room is full");
        if (seat != null && amount != room.getBuyIn()) bad("INVALID_BUY_IN", "Buy-in must equal the configured room buy-in");
        if (seat != null) {
            try { accounts.debit(userId, amount); }
            catch (InsufficientAccountChipsException ex) { throw conflict("INSUFFICIENT_CHIPS", "Insufficient account chips"); }
        }
        try {
            if (member == null) member = new RoomPlayerEntity(roomId, userId, seat, seat == null ? RoomPlayerState.SPECTATING : RoomPlayerState.NOT_READY, amount);
            else member.join(seat, amount);
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("SEAT_OR_MEMBERSHIP_CONFLICT", "Seat or membership changed concurrently");
        }
        room.recordActivity(clock.instant());
        RoomDetailResponse result = snapshot(roomId);
        events.publishEvent(new RoomChangedEvent(roomId, RoomEventType.PLAYER_JOINED, result,
                RoomEventType.PLAYER_COUNT_CHANGED, result.room()));
        return result;
    }

    @Transactional
    public RoomDetailResponse setReady(Long userId, Long roomId, boolean ready) {
        RoomEntity room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room not found"));
        if (room.getStatus() != RoomStatus.WAITING) throw conflict("INVALID_ROOM_STATE", "Room does not accept readiness changes");
        RoomPlayerEntity member = members.findByRoomIdAndUserIdForUpdate(roomId, userId)
                .filter(RoomPlayerEntity::isActive).orElseThrow(() -> forbidden("NOT_ROOM_MEMBER", "Active membership required"));
        if (member.getSeatNumber() == null) throw forbidden("SPECTATOR_CANNOT_READY", "Spectators cannot ready");
        member.setReady(ready); room.recordActivity(clock.instant());
        RoomDetailResponse result = snapshot(roomId);
        events.publishEvent(new RoomChangedEvent(roomId,
                ready ? RoomEventType.PLAYER_READY : RoomEventType.PLAYER_UNREADY, result, null, null));
        return result;
    }

    @Transactional
    public RoomDetailResponse leave(Long userId, Long roomId) {
        RoomEntity room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room not found"));
        if (room.getStatus() != RoomStatus.WAITING) throw conflict("ACTIVE_GAME_LEAVE_UNAVAILABLE", "Poker Engine must handle active-game leave");
        RoomPlayerEntity member = members.findByRoomIdAndUserIdForUpdate(roomId, userId)
                .filter(RoomPlayerEntity::isActive).orElseThrow(() -> notFound("MEMBERSHIP_NOT_FOUND", "Active membership not found"));
        accounts.credit(userId, member.leave(clock.instant()));
        List<RoomPlayerEntity> remaining = members.findAllByRoomIdAndLeftAtIsNullOrderById(roomId);
        if (room.getOwnerUserId().equals(userId)) {
            if (remaining.isEmpty()) room.close(clock.instant());
            else room.transferOwnershipTo(remaining.get(0).getUserId(), clock.instant());
        } else room.recordActivity(clock.instant());
        RoomDetailResponse result = snapshot(roomId);
        events.publishEvent(new RoomChangedEvent(roomId, RoomEventType.PLAYER_LEFT, result,
                room.getStatus() == RoomStatus.CLOSED ? RoomEventType.ROOM_CLOSED : RoomEventType.PLAYER_COUNT_CHANGED,
                result.room()));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(Long roomId, Long userId) {
        return members.findByRoomIdAndUserId(roomId, userId).filter(RoomPlayerEntity::isActive).isPresent();
    }

    private RoomSummaryResponse summary(RoomEntity room) {
        return new RoomSummaryResponse(room.getId(), room.getName(), room.getOwnerUserId(), room.getRoomType(),
                room.getPasswordHash() != null, room.getStatus(), members.countByRoomIdAndSeatNumberIsNotNullAndLeftAtIsNull(room.getId()),
                room.getMaxPlayers(), room.getSmallBlind(), room.getBigBlind(), room.getBuyIn());
    }
    private RoomEntity room(Long id) { return rooms.findById(id).orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room not found")); }
    private static void bad(String code, String message) { throw new RoomBusinessException(code, message, HttpStatus.BAD_REQUEST); }
    private static RoomBusinessException conflict(String code, String message) { return new RoomBusinessException(code, message, HttpStatus.CONFLICT); }
    private static RoomBusinessException forbidden(String code, String message) { return new RoomBusinessException(code, message, HttpStatus.FORBIDDEN); }
    private static RoomBusinessException notFound(String code, String message) { return new RoomBusinessException(code, message, HttpStatus.NOT_FOUND); }
}
