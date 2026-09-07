package com.ptit.poker.game.infrastructure.realtime;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandTransitionPropertiesTests {
    @Test
    void defaultsToASixSecondCompletedHandDwell() {
        assertThat(new HandTransitionProperties().interHandDelay()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void acceptsAnExplicitOverrideAndRejectsNonPositiveDelays() {
        HandTransitionProperties properties = new HandTransitionProperties();
        properties.setInterHandDelay(Duration.ofMillis(250));
        assertThat(properties.interHandDelay()).isEqualTo(Duration.ofMillis(250));
        assertThatThrownBy(() -> properties.setInterHandDelay(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
