package com.ptit.poker.game.infrastructure.persistence;

import jakarta.persistence.*;

@Entity @Table(name = "uncalled_bet_returns")
public class UncalledBetReturnEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "poker_hand_id", nullable = false) private Long pokerHandId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false) private long amount;
    protected UncalledBetReturnEntity() {}
    public UncalledBetReturnEntity(Long pokerHandId, Long userId, long amount) {
        this.pokerHandId = pokerHandId; this.userId = userId; this.amount = amount;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public long getAmount() { return amount; }
}
