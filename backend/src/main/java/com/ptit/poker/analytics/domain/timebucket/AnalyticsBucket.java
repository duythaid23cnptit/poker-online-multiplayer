package com.ptit.poker.analytics.domain.timebucket;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

public final class AnalyticsBucket {
    private AnalyticsBucket() {}
    public static LocalDate day(Instant finishedAt, ZoneId zone) { return finishedAt.atZone(zone).toLocalDate(); }
    public static LocalDate week(Instant finishedAt, ZoneId zone) { return monday(day(finishedAt, zone)); }
    public static LocalDate monday(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    public static Instant start(LocalDate date, ZoneId zone) { return date.atStartOfDay(zone).toInstant(); }
}
