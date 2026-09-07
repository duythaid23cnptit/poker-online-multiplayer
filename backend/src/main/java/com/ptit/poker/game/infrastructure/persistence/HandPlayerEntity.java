package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.game.domain.state.PokerPlayerState;
import jakarta.persistence.*;

@Entity @Table(name = "hand_players")
public class HandPlayerEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "poker_hand_id", nullable = false) private Long pokerHandId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "seat_number", nullable = false) private int seatNumber;
    @Column(name = "starting_table_chips", nullable = false) private long startingTableChips;
    @Column(name = "ending_table_chips", nullable = false) private long endingTableChips;
    @Column(name = "total_committed", nullable = false) private long totalCommitted;
    @Enumerated(EnumType.STRING) @Column(name = "participation_state", nullable = false, length = 20)
    private PokerPlayerState participationState;
    @Column(name = "connected_at_end", nullable = false) private boolean connectedAtEnd;
    @Column(name = "leaving_at_end", nullable = false) private boolean leavingAtEnd;
    @Column(name = "hole_cards", length = 8) private String holeCards;
    protected HandPlayerEntity() {}
    public HandPlayerEntity(Long pokerHandId, Long userId, int seatNumber, long startingTableChips,
                            long endingTableChips, long totalCommitted, PokerPlayerState participationState,
                            boolean connectedAtEnd, boolean leavingAtEnd, String holeCards) {
        this.pokerHandId = pokerHandId; this.userId = userId; this.seatNumber = seatNumber;
        this.startingTableChips = startingTableChips; this.endingTableChips = endingTableChips;
        this.totalCommitted = totalCommitted; this.participationState = participationState;
        this.connectedAtEnd = connectedAtEnd; this.leavingAtEnd = leavingAtEnd; this.holeCards = holeCards;
    }
    public Long getId() { return id; }
    public Long getPokerHandId() { return pokerHandId; }
    public Long getUserId() { return userId; }
    public int getSeatNumber() { return seatNumber; }
    public long getEndingTableChips() { return endingTableChips; }
    public PokerPlayerState getParticipationState() { return participationState; }
    public boolean isConnectedAtEnd() { return connectedAtEnd; }
    public boolean isLeavingAtEnd() { return leavingAtEnd; }
    public String getHoleCards() { return holeCards; }
}
