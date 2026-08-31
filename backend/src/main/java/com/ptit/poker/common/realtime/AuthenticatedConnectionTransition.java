package com.ptit.poker.common.realtime;

import java.util.Objects;

/** A process-local fact emitted for an effective authenticated STOMP session edge. */
public record AuthenticatedConnectionTransition(long userId, Type type) {
    public AuthenticatedConnectionTransition {
        if (userId <= 0) throw new IllegalArgumentException("Connection user must be positive");
        Objects.requireNonNull(type);
    }

    public enum Type {
        FIRST_CONNECTION,
        LAST_DISCONNECT
    }
}
