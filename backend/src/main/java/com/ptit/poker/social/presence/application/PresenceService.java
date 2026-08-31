package com.ptit.poker.social.presence.application;

import com.ptit.poker.player.domain.PresenceStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class PresenceService {
    private final PresencePersistencePort presence;
    private final ApplicationEventPublisher events;

    public PresenceService(PresencePersistencePort presence, ApplicationEventPublisher events) {
        this.presence = presence; this.events = events;
    }

    @Transactional public boolean online(long userId) { return transition(userId, PresenceStatus.ONLINE); }
    @Transactional public boolean offline(long userId) { return transition(userId, PresenceStatus.OFFLINE); }

    private boolean transition(long userId, PresenceStatus status) {
        boolean changed = presence.transition(userId, status);
        if (changed) events.publishEvent(new PresenceChanged(userId, status));
        return changed;
    }
}
