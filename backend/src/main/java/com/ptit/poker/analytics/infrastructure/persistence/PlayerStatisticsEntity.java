package com.ptit.poker.analytics.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="player_statistics")
public class PlayerStatisticsEntity {
    @Id @Column(name="user_id") private Long userId;
    @Column(name="total_games",nullable=false) private long totalGames;
    @Column(name="total_hands",nullable=false) private long totalHands;
    @Column(name="total_wins",nullable=false) private long totalWins;
    @Column(name="total_losses",nullable=false) private long totalLosses;
    @Column(name="win_rate",nullable=false,precision=7,scale=2) private BigDecimal winRate;
    @Column(name="total_chips_won",nullable=false) private long totalChipsWon;
    @Column(name="total_chips_lost",nullable=false) private long totalChipsLost;
    @Column(name="net_chip",nullable=false) private long netChip;
    @Column(name="largest_pot_won",nullable=false) private long largestPotWon;
    @Column(name="average_playing_seconds",nullable=false) private long averagePlayingSeconds;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected PlayerStatisticsEntity() {}
    public Long getUserId(){return userId;} public long getTotalGames(){return totalGames;} public long getTotalHands(){return totalHands;}
    public long getTotalWins(){return totalWins;} public long getTotalLosses(){return totalLosses;} public BigDecimal getWinRate(){return winRate;}
    public long getTotalChipsWon(){return totalChipsWon;} public long getTotalChipsLost(){return totalChipsLost;} public long getNetChip(){return netChip;}
    public long getLargestPotWon(){return largestPotWon;} public long getAveragePlayingSeconds(){return averagePlayingSeconds;}
}
