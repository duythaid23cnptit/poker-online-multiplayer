package com.ptit.poker.analytics.application;

import com.ptit.poker.analytics.domain.PlayerStatistics;
import java.math.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class PlayerStatisticsCalculator {
    public PlayerStatistics calculate(long userId,List<PlayerStatisticsHistoryPort.CompletedHandFact> hands,long largestAward){
        if(hands.isEmpty())return PlayerStatistics.zero(userId);
        long won=0,lost=0;Map<Long,Session> sessions=new HashMap<>();
        for(var hand:hands){long delta=Math.subtractExact(hand.endingTableChips(),hand.startingTableChips());
            if(delta>0)won=Math.addExact(won,delta);else if(delta<0)lost=Math.addExact(lost,Math.negateExact(delta));
            sessions.compute(hand.gameSessionId(),(id,value)->value==null
                    ?new Session(delta,hand.startedAt(),hand.finishedAt())
                    :value.add(delta,hand.startedAt(),hand.finishedAt()));}
        long wins=0,losses=0,totalSeconds=0;
        for(Session session:sessions.values()){if(session.net>0)wins++;else if(session.net<0)losses++;
            totalSeconds=Math.addExact(totalSeconds,Duration.between(session.start,session.end).getSeconds());}
        long games=sessions.size();BigDecimal rate=BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(games),2,RoundingMode.HALF_UP);
        return new PlayerStatistics(userId,games,hands.size(),wins,losses,rate,won,lost,
                Math.subtractExact(won,lost),largestAward,totalSeconds/games);
    }
    private record Session(long net,Instant start,Instant end){Session add(long delta,Instant handStart,Instant handEnd){
        return new Session(Math.addExact(net,delta),start.isBefore(handStart)?start:handStart,end.isAfter(handEnd)?end:handEnd);}}
}
