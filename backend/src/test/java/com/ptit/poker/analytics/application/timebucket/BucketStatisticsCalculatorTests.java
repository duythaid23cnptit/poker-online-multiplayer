package com.ptit.poker.analytics.application.timebucket;

import static org.assertj.core.api.Assertions.assertThat;import java.time.Instant;import java.util.List;import org.junit.jupiter.api.Test;

class BucketStatisticsCalculatorTests {
    private final BucketStatisticsCalculator calculator=new BucketStatisticsCalculator();
    @Test void classifiesPositiveNegativeAndZeroNetHands(){var value=calculator.calculate(List.of(hand(1,0,10,100,200),hand(1,10,20,200,160),hand(1,20,30,160,160)),0);assertThat(value.handsWon()).isOne();assertThat(value.handsLost()).isOne();assertThat(value.handsTied()).isOne();}
    @Test void sumsPositiveAndAbsoluteNegativeChips(){var value=calculator.calculate(List.of(hand(1,0,1,0,100),hand(1,1,2,100,60),hand(1,2,3,60,120)),0);assertThat(value.chipsWon()).isEqualTo(160);assertThat(value.chipsLost()).isEqualTo(40);assertThat(value.netChips()).isEqualTo(120);}
    @Test void countsDistinctSessions(){var value=calculator.calculate(List.of(hand(1,0,5,0,1),hand(1,5,10,1,2),hand(2,20,25,0,1)),0);assertThat(value.sessionsParticipated()).isEqualTo(2);}
    @Test void playingTimeSumsPerSessionIntervalsWithoutInterSessionGap(){var value=calculator.calculate(List.of(hand(1,0,10,0,1),hand(1,20,30,1,2),hand(2,100,110,0,1)),0);assertThat(value.playingTimeSeconds()).isEqualTo(40);}
    @Test void carriesActualLargestAwardAndDeterministicInputOrder(){var a=calculator.calculate(List.of(hand(2,10,20,100,90),hand(1,0,5,0,10)),77);var b=calculator.calculate(List.of(hand(1,0,5,0,10),hand(2,10,20,100,90)),77);assertThat(a).isEqualTo(b);assertThat(a.largestPotWon()).isEqualTo(77);}
    private static TimeBucketAnalyticsHistoryPort.CompletedHand hand(long session,long start,long end,long chipsBefore,long chipsAfter){return new TimeBucketAnalyticsHistoryPort.CompletedHand(session,Instant.EPOCH.plusSeconds(start),Instant.EPOCH.plusSeconds(end),chipsBefore,chipsAfter);}
}
