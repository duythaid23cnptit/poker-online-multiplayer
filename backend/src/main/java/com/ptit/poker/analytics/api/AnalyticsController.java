package com.ptit.poker.analytics.api;

import com.ptit.poker.analytics.application.timebucket.TimeBucketAnalyticsService;import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;import java.time.LocalDate;import org.springframework.context.annotation.Profile;import org.springframework.format.annotation.DateTimeFormat;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;

@RestController @Profile("!bootstrap") @RequestMapping("/api/v1/analytics/me")
public class AnalyticsController {
    private final TimeBucketAnalyticsService analytics;public AnalyticsController(TimeBucketAnalyticsService analytics){this.analytics=analytics;}
    @GetMapping("/daily") public java.util.List<TimeBucketAnalyticsService.Daily> daily(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return analytics.daily(user.userId(),from,to);}
    @GetMapping("/weekly") public java.util.List<TimeBucketAnalyticsService.Weekly> weekly(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return analytics.weekly(user.userId(),from,to);}
    @GetMapping("/summary") public TimeBucketAnalyticsService.Summary summary(@AuthenticationPrincipal AuthenticatedUser user){return analytics.summary(user.userId());}
}
