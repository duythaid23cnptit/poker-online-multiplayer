package com.ptit.poker.analytics.application.timebucket;

import java.time.Instant;
import java.util.List;

public interface TimeBucketAnalyticsHistoryPort {
    List<CompletedHand> completedHands(long userId, Instant fromInclusive, Instant toExclusive);
    long largestPotAward(long userId, Instant fromInclusive, Instant toExclusive);
    List<SessionParticipantHand> completedSessionHands(long sessionId);
    record CompletedHand(long sessionId,Instant startedAt,Instant finishedAt,long startingChips,long endingChips){}
    record SessionParticipantHand(long userId,Instant finishedAt){}
}
