package com.ptit.poker.analytics.application;

import com.ptit.poker.analytics.domain.PlayerStatistics;
import com.ptit.poker.analytics.infrastructure.persistence.*;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service @Profile("!bootstrap")
public class PlayerStatisticsService implements PlayerStatisticsRefreshPort {
    private final PlayerStatisticsHistoryPort history;private final PlayerStatisticsRepository statistics;
    private final PlayerStatisticsCalculator calculator=new PlayerStatisticsCalculator();private final Clock clock;
    public PlayerStatisticsService(PlayerStatisticsHistoryPort history,PlayerStatisticsRepository statistics,Clock clock){
        this.history=history;this.statistics=statistics;this.clock=clock;}
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void refreshCompletedSession(long sessionId){for(long userId:history.participantUserIds(sessionId))refreshPlayerStatistics(userId);}
    @Transactional public void refreshPlayerStatistics(long userId){PlayerStatistics value=calculator.calculate(userId,
            history.completedHands(userId),history.largestPotAward(userId));statistics.replace(userId,value.totalGames(),value.totalHands(),
            value.totalWins(),value.totalLosses(),value.winRate(),value.totalChipsWon(),value.totalChipsLost(),value.netChip(),
            value.largestPotWon(),value.averagePlayingSeconds(),clock.instant());}
    @Transactional(readOnly=true) public PlayerStatistics get(long userId){return statistics.findById(userId).map(PlayerStatisticsService::domain)
            .orElseGet(()->PlayerStatistics.zero(userId));}
    private static PlayerStatistics domain(PlayerStatisticsEntity e){return new PlayerStatistics(e.getUserId(),e.getTotalGames(),e.getTotalHands(),
            e.getTotalWins(),e.getTotalLosses(),e.getWinRate(),e.getTotalChipsWon(),e.getTotalChipsLost(),e.getNetChip(),
            e.getLargestPotWon(),e.getAveragePlayingSeconds());}
}
