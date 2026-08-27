package com.ptit.poker.game.infrastructure.realtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="poker.game")
public class TurnTimerProperties implements com.ptit.poker.game.application.realtime.TurnTimerConfiguration {
    private Duration turnTimeout=Duration.ofSeconds(30);
    private Duration timerUpdateCadence=Duration.ofSeconds(1);
    public Duration getTurnTimeout(){return turnTimeout;}
    public void setTurnTimeout(Duration value){if(value==null||value.isZero()||value.isNegative())throw new IllegalArgumentException("turn-timeout must be positive");turnTimeout=value;}
    public Duration getTimerUpdateCadence(){return timerUpdateCadence;}
    public void setTimerUpdateCadence(Duration value){if(value==null||value.isZero()||value.isNegative())throw new IllegalArgumentException("timer-update-cadence must be positive");timerUpdateCadence=value;}
    @Override public Duration turnTimeout(){return turnTimeout;}
    @Override public Duration updateCadence(){return timerUpdateCadence;}
}
