package com.ptit.poker.social.presence.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresenceStartupReconcilerTests {
    @Test void resetsPersistedNonOfflinePresenceAtStartup() {
        PresencePersistencePort persistence = mock(PresencePersistencePort.class);
        when(persistence.resetAllOffline()).thenReturn(3);
        assertThat(new PresenceStartupReconciler(persistence).reconcile()).isEqualTo(3);
    }
}
