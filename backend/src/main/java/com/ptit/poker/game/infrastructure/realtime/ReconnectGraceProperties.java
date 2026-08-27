package com.ptit.poker.game.infrastructure.realtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("poker.game")
public record ReconnectGraceProperties(Duration reconnectGrace) {
    public ReconnectGraceProperties {
        reconnectGrace = reconnectGrace == null ? Duration.ofSeconds(60) : reconnectGrace;
        if (reconnectGrace.isNegative() || reconnectGrace.isZero())
            throw new IllegalArgumentException("poker.game.reconnect-grace must be positive");
    }
}
