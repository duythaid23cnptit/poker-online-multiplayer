package com.ptit.poker.game.application.realtime;

import java.time.Duration;

public record ReconnectGraceConfiguration(Duration grace) {
    public ReconnectGraceConfiguration {
        if (grace == null || grace.isNegative() || grace.isZero())
            throw new IllegalArgumentException("reconnect grace must be positive");
    }
}
