package com.ptit.poker.game.infrastructure.persistence;

import com.ptit.poker.game.domain.state.GamePhase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "poker_hands")
public class PokerHandEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "game_session_id", nullable = false) private Long gameSessionId;
    @Column(name = "hand_number", nullable = false) private long handNumber;
    @Column(name = "dealer_seat", nullable = false) private int dealerSeat;
    @Column(name = "small_blind_seat", nullable = false) private int smallBlindSeat;
    @Column(name = "big_blind_seat", nullable = false) private int bigBlindSeat;
    @Column(name = "small_blind_amount", nullable = false) private long smallBlindAmount;
    @Column(name = "big_blind_amount", nullable = false) private long bigBlindAmount;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Enumerated(EnumType.STRING) @Column(name = "final_phase", nullable = false, length = 20) private GamePhase finalPhase;
    @Column(name = "board_cards", nullable = false, length = 32) private String boardCards;
    @Enumerated(EnumType.STRING) @Column(name = "end_reason", length = 30) private HandEndReason endReason;
    protected PokerHandEntity() {}
    public PokerHandEntity(Long gameSessionId, long handNumber, int dealerSeat, int smallBlindSeat,
                           int bigBlindSeat, long smallBlindAmount, long bigBlindAmount,
                           Instant startedAt, Instant endedAt, GamePhase finalPhase,
                           String boardCards, HandEndReason endReason) {
        this.gameSessionId = gameSessionId; this.handNumber = handNumber; this.dealerSeat = dealerSeat;
        this.smallBlindSeat = smallBlindSeat; this.bigBlindSeat = bigBlindSeat;
        this.smallBlindAmount = smallBlindAmount; this.bigBlindAmount = bigBlindAmount;
        this.startedAt = startedAt; this.endedAt = endedAt; this.finalPhase = finalPhase;
        this.boardCards = boardCards; this.endReason = endReason;
    }
    public Long getId() { return id; }
    public Long getGameSessionId() { return gameSessionId; }
    public long getHandNumber() { return handNumber; }
    public int getDealerSeat() { return dealerSeat; }
    public int getSmallBlindSeat() { return smallBlindSeat; }
    public int getBigBlindSeat() { return bigBlindSeat; }
    public GamePhase getFinalPhase() { return finalPhase; }
    public String getBoardCards() { return boardCards; }
    public HandEndReason getEndReason() { return endReason; }
    public void complete(Instant endedAt, GamePhase finalPhase, String boardCards, HandEndReason endReason) {
        if (this.endReason != null) throw new IllegalStateException("poker hand is already completed");
        this.endedAt = endedAt; this.finalPhase = finalPhase; this.boardCards = boardCards; this.endReason = endReason;
    }
}
