package com.ptit.poker.analytics.domain;

import java.math.BigDecimal;

public record PlayerStatistics(long userId,long totalGames,long totalHands,long totalWins,long totalLosses,
                               BigDecimal winRate,long totalChipsWon,long totalChipsLost,long netChip,
                               long largestPotWon,long averagePlayingSeconds) {
    public static PlayerStatistics zero(long userId){return new PlayerStatistics(userId,0,0,0,0,
            BigDecimal.ZERO.setScale(2),0,0,0,0,0);}
}
