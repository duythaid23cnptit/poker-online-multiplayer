package com.ptit.poker.room.infrastructure.persistence;

import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
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
@Table(name = "rooms")
public class RoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "small_blind", nullable = false)
    private long smallBlind;

    @Column(name = "big_blind", nullable = false)
    private long bigBlind;

    @Column(name = "buy_in", nullable = false)
    private long buyIn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected RoomEntity() {
    }

    public RoomEntity(String name, Long ownerUserId, RoomType roomType, String passwordHash,
                      int maxPlayers, long smallBlind, long bigBlind, long buyIn,
                      RoomStatus status, Instant lastActivityAt) {
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.roomType = roomType;
        this.passwordHash = passwordHash;
        this.maxPlayers = maxPlayers;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.buyIn = buyIn;
        this.status = status;
        this.lastActivityAt = lastActivityAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() { return name; }
    public Long getOwnerUserId() { return ownerUserId; }
    public RoomType getRoomType() { return roomType; }
    public String getPasswordHash() { return passwordHash; }
    public int getMaxPlayers() { return maxPlayers; }
    public long getSmallBlind() { return smallBlind; }
    public long getBigBlind() { return bigBlind; }
    public long getBuyIn() { return buyIn; }
    public RoomStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }

    public void transferOwnershipTo(Long userId, Instant activityAt) {
        ownerUserId = userId;
        lastActivityAt = activityAt;
    }

    public void close(Instant activityAt) {
        status = RoomStatus.CLOSED;
        lastActivityAt = activityAt;
    }

    public void finish(Instant activityAt) {
        status = RoomStatus.FINISHED;
        lastActivityAt = activityAt;
    }

    public void recordActivity(Instant activityAt) { lastActivityAt = activityAt; }
}
