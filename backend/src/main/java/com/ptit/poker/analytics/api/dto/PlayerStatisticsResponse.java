package com.ptit.poker.analytics.api.dto;

import com.ptit.poker.analytics.domain.PlayerStatistics;
import java.math.BigDecimal;

public record PlayerStatisticsResponse(long userId,long totalGames,long totalHands,long totalWins,long totalLosses,
                                       BigDecimal winRate,long totalChipsWon,long totalChipsLost,long netChip,
                                       long largestPotWon,long averagePlayingSeconds) {
    public static PlayerStatisticsResponse from(PlayerStatistics value){return new PlayerStatisticsResponse(value.userId(),value.totalGames(),
            value.totalHands(),value.totalWins(),value.totalLosses(),value.winRate(),value.totalChipsWon(),value.totalChipsLost(),
            value.netChip(),value.largestPotWon(),value.averagePlayingSeconds());}
}
