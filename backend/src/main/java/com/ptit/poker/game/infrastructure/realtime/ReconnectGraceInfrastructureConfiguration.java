package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.game.application.realtime.ReconnectGraceConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!bootstrap")
@EnableConfigurationProperties(ReconnectGraceProperties.class)
class ReconnectGraceInfrastructureConfiguration {
    @Bean ReconnectGraceConfiguration reconnectGraceConfiguration(ReconnectGraceProperties properties) {
        return new ReconnectGraceConfiguration(properties.reconnectGrace());
    }
}
