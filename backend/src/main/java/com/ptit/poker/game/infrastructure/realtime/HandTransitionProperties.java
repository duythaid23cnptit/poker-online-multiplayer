package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.game.application.realtime.HandTransitionConfiguration;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "poker.game")
public class HandTransitionProperties implements HandTransitionConfiguration {
    private Duration interHandDelay = Duration.ofSeconds(6);

    public Duration getInterHandDelay() { return interHandDelay; }

    public void setInterHandDelay(Duration value) {
        if (value == null || value.isNegative() || value.isZero())
            throw new IllegalArgumentException("inter-hand-delay must be positive");
        interHandDelay = value;
    }

    @Override
    public Duration interHandDelay() { return interHandDelay; }
}
