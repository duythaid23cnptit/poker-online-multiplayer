package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.game.domain.pot.PotType;
import jakarta.persistence.*;

@Entity @Table(name = "pots")
public class PotEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "poker_hand_id", nullable = false) private Long pokerHandId;
    @Column(name = "pot_index", nullable = false) private int potIndex;
    @Enumerated(EnumType.STRING) @Column(name = "pot_type", nullable = false, length = 10) private PotType potType;
    @Column(nullable = false) private long amount;
    @Column(name = "contribution_cap", nullable = false) private long contributionCap;
    protected PotEntity() {}
    public PotEntity(Long pokerHandId, int potIndex, PotType potType, long amount, long contributionCap) {
        this.pokerHandId = pokerHandId; this.potIndex = potIndex; this.potType = potType;
        this.amount = amount; this.contributionCap = contributionCap;
    }
    public Long getId() { return id; }
    public int getPotIndex() { return potIndex; }
    public PotType getPotType() { return potType; }
    public long getAmount() { return amount; }
}
