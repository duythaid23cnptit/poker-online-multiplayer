package com.ptit.poker.analytics.application;

import java.time.Instant;
import java.util.List;

public interface PlayerStatisticsHistoryPort {
    List<Long> participantUserIds(long gameSessionId);
    List<CompletedHandFact> completedHands(long userId);
    long largestPotAward(long userId);
    record CompletedHandFact(long gameSessionId,long pokerHandId,Instant startedAt,Instant finishedAt,
                             long startingTableChips,long endingTableChips) {}
}
