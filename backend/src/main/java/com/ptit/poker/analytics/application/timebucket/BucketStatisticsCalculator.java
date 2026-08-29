package com.ptit.poker.analytics.application.timebucket;

import com.ptit.poker.analytics.domain.timebucket.BucketStatistics;
import java.time.*;
import java.util.*;

public final class BucketStatisticsCalculator {
    public BucketStatistics calculate(List<TimeBucketAnalyticsHistoryPort.CompletedHand> hands,long largestAward){
        if(hands.isEmpty())return BucketStatistics.zero();long won=0,lost=0,wins=0,losses=0,ties=0;Map<Long,Interval> sessions=new HashMap<>();
        for(var hand:hands){long net=Math.subtractExact(hand.endingChips(),hand.startingChips());if(net>0){wins++;won=Math.addExact(won,net);}else if(net<0){losses++;lost=Math.addExact(lost,Math.negateExact(net));}else ties++;
            sessions.compute(hand.sessionId(),(id,current)->current==null?new Interval(hand.startedAt(),hand.finishedAt()):current.include(hand.startedAt(),hand.finishedAt()));}
        long seconds=0;for(Interval interval:sessions.values())seconds=Math.addExact(seconds,Math.max(0,Duration.between(interval.start,interval.end).getSeconds()));
        return new BucketStatistics(hands.size(),wins,losses,ties,won,lost,Math.subtractExact(won,lost),largestAward,seconds,sessions.size());
    }
    private record Interval(Instant start,Instant end){Interval include(Instant s,Instant e){return new Interval(start.isBefore(s)?start:s,end.isAfter(e)?end:e);}}
}
