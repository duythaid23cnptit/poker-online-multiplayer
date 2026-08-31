package com.ptit.poker.social.presence.application;

import com.ptit.poker.player.domain.PresenceStatus;

import java.util.Objects;

public record PresenceChanged(long userId, PresenceStatus presenceStatus) {
    public PresenceChanged {
        if (userId <= 0) throw new IllegalArgumentException("Presence user must be positive");
        Objects.requireNonNull(presenceStatus);
    }
}
