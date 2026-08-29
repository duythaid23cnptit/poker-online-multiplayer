package com.ptit.poker.analytics.domain.timebucket;

import static org.assertj.core.api.Assertions.assertThat;import java.time.*;import org.junit.jupiter.api.Test;

class AnalyticsBucketTests {
    private static final ZoneId ZONE=ZoneId.of("Asia/Bangkok");
    @Test void usesBangkokCalendarDay(){assertThat(AnalyticsBucket.day(Instant.parse("2026-08-28T16:59:59Z"),ZONE)).isEqualTo(LocalDate.of(2026,8,28));}
    @Test void midnightStartsNextDay(){assertThat(AnalyticsBucket.day(Instant.parse("2026-08-28T17:00:00Z"),ZONE)).isEqualTo(LocalDate.of(2026,8,29));}
    @Test void mondayIsWeekStart(){assertThat(AnalyticsBucket.monday(LocalDate.of(2026,8,26))).isEqualTo(LocalDate.of(2026,8,24));}
    @Test void sundayToMondayChangesWeek(){assertThat(AnalyticsBucket.week(Instant.parse("2026-08-30T16:59:59Z"),ZONE)).isEqualTo(LocalDate.of(2026,8,24));assertThat(AnalyticsBucket.week(Instant.parse("2026-08-30T17:00:00Z"),ZONE)).isEqualTo(LocalDate.of(2026,8,31));}
    @Test void isoYearBoundaryUsesMondayDate(){assertThat(AnalyticsBucket.monday(LocalDate.of(2027,1,1))).isEqualTo(LocalDate.of(2026,12,28));}
}
