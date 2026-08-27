package com.ptit.poker.game.application.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RealtimeConnectionRegistryTests {
    private final RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry();

    @Test void firstAndAdditionalSessionsAreDistinguished() {
        assertThat(registry.register("a", 7)).isTrue();
        assertThat(registry.register("b", 7)).isFalse();
        assertThat(registry.activeSessionCount(7)).isEqualTo(2);
    }

    @Test void onlyLastSessionDisconnectTransitionsUser() {
        registry.register("a", 7); registry.register("b", 7);
        assertThat(registry.unregister("a").lastSession()).isFalse();
        assertThat(registry.unregister("b").lastSession()).isTrue();
    }

    @Test void duplicateAndObsoleteDisconnectAreIdempotent() {
        registry.register("new", 7);
        assertThat(registry.unregister("old").knownSession()).isFalse();
        assertThat(registry.activeSessionCount(7)).isOne();
        registry.unregister("new");
        assertThat(registry.unregister("new").knownSession()).isFalse();
    }

    @Test void sessionIdCannotBeReassignedWhileActive() {
        assertThat(registry.register("same", 7)).isTrue();
        assertThat(registry.register("same", 8)).isFalse();
        assertThat(registry.activeSessionCount(7)).isOne();
        assertThat(registry.activeSessionCount(8)).isZero();
    }
}
