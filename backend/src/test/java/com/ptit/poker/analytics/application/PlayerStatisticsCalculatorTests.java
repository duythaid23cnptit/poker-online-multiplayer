package com.ptit.poker.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerStatisticsCalculatorTests {
    private final PlayerStatisticsCalculator calculator=new PlayerStatisticsCalculator();
    private final Instant base=Instant.parse("2026-08-28T00:00:00Z");
    @Test void aggregatesWinsLossesNeutralHandsAndSessionDurations(){var value=calculator.calculate(7,List.of(
            hand(1,1,100,160,0,60),hand(1,2,160,150,70,130),
            hand(2,3,100,50,200,260),hand(3,4,100,100,300,390)),75);
        assertThat(value.totalGames()).isEqualTo(3);assertThat(value.totalHands()).isEqualTo(4);
        assertThat(value.totalWins()).isEqualTo(1);assertThat(value.totalLosses()).isEqualTo(1);
        assertThat(value.winRate()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(value.totalChipsWon()).isEqualTo(60);assertThat(value.totalChipsLost()).isEqualTo(60);
        assertThat(value.netChip()).isZero();assertThat(value.largestPotWon()).isEqualTo(75);
        assertThat(value.averagePlayingSeconds()).isEqualTo(93);
    }
    @Test void zeroHistoryReturnsZeroProjection(){assertThat(calculator.calculate(9,List.of(),0)).isEqualTo(
            com.ptit.poker.analytics.domain.PlayerStatistics.zero(9));}
    @Test void splitSideAndUncalledSemanticsUseActualDeltaAndActualAward(){var value=calculator.calculate(7,List.of(
            hand(1,1,1000,1150,0,10),hand(1,2,1150,1000,20,30)),151);
        assertThat(value.totalChipsWon()).isEqualTo(150);assertThat(value.totalChipsLost()).isEqualTo(150);
        assertThat(value.largestPotWon()).isEqualTo(151);assertThat(value.netChip()).isZero();}
    private PlayerStatisticsHistoryPort.CompletedHandFact hand(long session,long id,long start,long end,long from,long to){
        return new PlayerStatisticsHistoryPort.CompletedHandFact(session,id,base.plusSeconds(from),base.plusSeconds(to),start,end);}
}
