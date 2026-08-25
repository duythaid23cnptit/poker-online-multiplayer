package com.ptit.poker.room.infrastructure.persistence;

import com.ptit.poker.room.domain.RoomPlayerState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "room_players")
public class RoomPlayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "player_state", nullable = false, length = 20)
    private RoomPlayerState playerState;

    @Column(name = "table_chips", nullable = false)
    private long tableChips;

    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected RoomPlayerEntity() {
    }

    public RoomPlayerEntity(Long roomId, Long userId, Integer seatNumber,
                            RoomPlayerState playerState, long tableChips) {
        this.roomId = roomId;
        this.userId = userId;
        this.seatNumber = seatNumber;
        this.playerState = playerState;
        this.tableChips = tableChips;
    }

    public Long getId() {
        return id;
    }
    public Long getRoomId() { return roomId; }
    public Long getUserId() { return userId; }
    public Integer getSeatNumber() { return seatNumber; }
    public RoomPlayerState getPlayerState() { return playerState; }
    public long getTableChips() { return tableChips; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLeftAt() { return leftAt; }

    public boolean isActive() { return leftAt == null; }

    public void join(Integer seat, long chips) {
        seatNumber = seat;
        playerState = seat == null ? RoomPlayerState.SPECTATING : RoomPlayerState.NOT_READY;
        tableChips = chips;
        leftAt = null;
    }

    public void setReady(boolean ready) {
        playerState = ready ? RoomPlayerState.READY : RoomPlayerState.NOT_READY;
    }

    public void markPlaying() {
        if (seatNumber == null || leftAt != null) throw new IllegalStateException("only a seated member can play");
        playerState = RoomPlayerState.PLAYING;
    }

    public void synchronizeTableChips(long chips) {
        if (chips < 0) throw new IllegalArgumentException("table chips must not be negative");
        tableChips = chips;
    }

    public long leave(Instant at) {
        long cashOut = tableChips;
        tableChips = 0;
        seatNumber = null;
        playerState = RoomPlayerState.SPECTATING;
        leftAt = at;
        return cashOut;
    }
}
