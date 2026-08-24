package com.ptit.poker.game.infrastructure.persistence;

import jakarta.persistence.*;

@Entity @Table(name = "pot_awards")
public class PotAwardEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "pot_id", nullable = false) private Long potId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "amount_awarded", nullable = false) private long amountAwarded;
    @Column(name = "odd_chip_amount", nullable = false) private long oddChipAmount;
    protected PotAwardEntity() {}
    public PotAwardEntity(Long potId, Long userId, long amountAwarded, long oddChipAmount) {
        this.potId = potId; this.userId = userId; this.amountAwarded = amountAwarded; this.oddChipAmount = oddChipAmount;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public long getAmountAwarded() { return amountAwarded; }
    public long getOddChipAmount() { return oddChipAmount; }
}
