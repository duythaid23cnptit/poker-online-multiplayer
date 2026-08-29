package com.ptit.poker.analytics.domain.timebucket;

public record BucketStatistics(long handsPlayed,long handsWon,long handsLost,long handsTied,long chipsWon,
        long chipsLost,long netChips,long largestPotWon,long playingTimeSeconds,long sessionsParticipated) {
    public static BucketStatistics zero(){return new BucketStatistics(0,0,0,0,0,0,0,0,0,0);}
}
