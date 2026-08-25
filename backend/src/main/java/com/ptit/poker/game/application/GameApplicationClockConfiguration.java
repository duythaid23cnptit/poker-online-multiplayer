package com.ptit.poker.game.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.time.Clock;

@Configuration
@Profile("!bootstrap")
class GameApplicationClockConfiguration {
    @Bean Clock gameApplicationClock() { return Clock.systemUTC(); }
}
