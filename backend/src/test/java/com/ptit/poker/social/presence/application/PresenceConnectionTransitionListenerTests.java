package com.ptit.poker.social.presence.application;

import com.ptit.poker.common.realtime.AuthenticatedConnectionTransition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PresenceConnectionTransitionListenerTests {
    private final PresenceService presence = mock(PresenceService.class);
    private final PresenceConnectionTransitionListener listener = new PresenceConnectionTransitionListener(presence);

    @Test void firstConnectionMarksPresenceOnline() {
        listener.onTransition(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.FIRST_CONNECTION));

        verify(presence).online(7);
    }

    @Test void lastDisconnectMarksPresenceOffline() {
        listener.onTransition(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.LAST_DISCONNECT));

        verify(presence).offline(7);
    }

    @Test void onlinePresenceFailureIsContained() {
        doThrow(new IllegalStateException("database unavailable")).when(presence).online(7);

        assertThatCode(() -> listener.onTransition(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.FIRST_CONNECTION))).doesNotThrowAnyException();
    }

    @Test void offlinePresenceFailureIsAlsoContained() {
        doThrow(new IllegalStateException("database unavailable")).when(presence).offline(7);

        assertThatCode(() -> listener.onTransition(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.LAST_DISCONNECT))).doesNotThrowAnyException();
    }
}
