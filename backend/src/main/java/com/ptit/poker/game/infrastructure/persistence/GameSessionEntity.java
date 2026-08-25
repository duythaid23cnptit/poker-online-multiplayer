package com.ptit.poker.game.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "game_sessions")
public class GameSessionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "room_id", nullable = false) private Long roomId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private GameSessionStatus status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    protected GameSessionEntity() {}
    public GameSessionEntity(Long roomId, GameSessionStatus status, Instant startedAt, Instant endedAt) {
        this.roomId = roomId; this.status = status; this.startedAt = startedAt; this.endedAt = endedAt;
    }
    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public GameSessionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void finish(Instant endedAt) {
        requireActive(); this.status = GameSessionStatus.FINISHED; this.endedAt = endedAt;
    }
    public void abort(Instant endedAt) {
        requireActive(); this.status = GameSessionStatus.ABORTED; this.endedAt = endedAt;
    }
    private void requireActive() {
        if (status != GameSessionStatus.ACTIVE) throw new IllegalStateException("game session is not ACTIVE");
    }
}
