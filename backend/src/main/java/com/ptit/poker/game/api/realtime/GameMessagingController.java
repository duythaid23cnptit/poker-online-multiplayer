package com.ptit.poker.game.api.realtime;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller @Profile("!bootstrap")
public class GameMessagingController {
    private final GameRealtimeApplicationService games;
    public GameMessagingController(GameRealtimeApplicationService games) { this.games = games; }

    @MessageMapping("/game/{gameId}/action")
    public void action(@DestinationVariable UUID gameId, @Valid GameActionMessage message, Principal principal) {
        AuthenticatedUser user = (AuthenticatedUser) ((Authentication) principal).getPrincipal();
        games.handleAction(gameId, user.userId(), message);
    }
}
