package com.ptit.poker.social.presence.application;

import com.ptit.poker.common.realtime.AuthenticatedConnectionTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
class PresenceConnectionTransitionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresenceConnectionTransitionListener.class);
    private final PresenceService presence;

    PresenceConnectionTransitionListener(PresenceService presence) {
        this.presence = presence;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void onTransition(AuthenticatedConnectionTransition transition) {
        try {
            switch (transition.type()) {
                case FIRST_CONNECTION -> presence.online(transition.userId());
                case LAST_DISCONNECT -> presence.offline(transition.userId());
            }
        } catch (RuntimeException failure) {
            LOGGER.error("Best-effort presence transition failed user={} type={}",
                    transition.userId(), transition.type(), failure);
        }
    }
}
