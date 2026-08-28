package com.ptit.poker.analytics.api;
import com.ptit.poker.analytics.application.ranking.RankingService;import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;import org.springframework.context.annotation.Profile;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @Profile("!bootstrap") @RequestMapping("/api/v1/rankings") public class RankingController {private final RankingService ranking;public RankingController(RankingService ranking){this.ranking=ranking;}
 @GetMapping("/me") public RankingService.CurrentRanking me(@AuthenticationPrincipal AuthenticatedUser user){return ranking.current(user.userId());}
 @GetMapping("/leaderboard") public RankingService.Page board(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ranking.leaderboard(page,size);}
 @GetMapping("/me/history") public java.util.List<RankingService.History> history(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ranking.history(user.userId(),page,size);}}
