package com.ptit.poker.social.presence.application;

import com.ptit.poker.player.domain.PresenceStatus;

import java.util.Optional;

public interface PresencePersistencePort {
    boolean transition(long userId, PresenceStatus status);
    Optional<PresenceStatus> find(long userId);
    int resetAllOffline();
}
