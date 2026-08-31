package com.ptit.poker.game.application.realtime;

import com.ptit.poker.common.realtime.AuthenticatedConnectionTransition;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
class GameConnectionTransitionListener {
    private final GameRealtimeApplicationService games;

    GameConnectionTransitionListener(GameRealtimeApplicationService games) {
        this.games = games;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    void onTransition(AuthenticatedConnectionTransition transition) {
        switch (transition.type()) {
            case FIRST_CONNECTION -> games.onFirstConnection(transition.userId());
            case LAST_DISCONNECT -> games.onLastDisconnect(transition.userId());
        }
    }
}
