package com.ptit.poker.game.application.realtime;

import com.ptit.poker.common.realtime.AuthenticatedConnectionTransition;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameConnectionTransitionListenerTests {
    private final GameRealtimeApplicationService games = mock(GameRealtimeApplicationService.class);
    private final GameConnectionTransitionListener listener = new GameConnectionTransitionListener(games);

    @Test void firstConnectionRetainsExistingGameReconnectCallback() {
        listener.onTransition(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.FIRST_CONNECTION));

        verify(games).onFirstConnection(7);
    }

    @Test void lastDisconnectRetainsExistingGameDisconnectCallback() {
        listener.onTransition(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.LAST_DISCONNECT));

        verify(games).onLastDisconnect(7);
    }

    @Test void gameFailureIsNotSilentlySwallowed() {
        doThrow(new IllegalStateException("game failure")).when(games).onFirstConnection(7);

        assertThatThrownBy(() -> listener.onTransition(new AuthenticatedConnectionTransition(7,
                AuthenticatedConnectionTransition.Type.FIRST_CONNECTION)))
                .isInstanceOf(IllegalStateException.class).hasMessage("game failure");
    }
}
