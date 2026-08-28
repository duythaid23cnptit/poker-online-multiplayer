package com.ptit.poker.analytics.api;

import com.ptit.poker.analytics.api.dto.PlayerStatisticsResponse;
import com.ptit.poker.analytics.application.PlayerStatisticsService;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!bootstrap") @RequestMapping("/api/v1/players/me/statistics")
public class PlayerStatisticsController {
    private final PlayerStatisticsService service;public PlayerStatisticsController(PlayerStatisticsService service){this.service=service;}
    @GetMapping public PlayerStatisticsResponse current(@AuthenticationPrincipal AuthenticatedUser principal){
        return PlayerStatisticsResponse.from(service.get(principal.userId()));}
}
