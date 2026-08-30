package com.ptit.poker.social.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class FriendshipNotificationListenerTests {
    private final SocialNotificationPort notifications = mock(SocialNotificationPort.class);
    private final FriendshipNotificationListener listener = new FriendshipNotificationListener(notifications);

    @Test
    void forwardsCommittedNotification() {
        FriendshipNotificationRequested event = event();

        listener.afterCommit(event);

        verify(notifications).send(event);
    }

    @Test
    void deliveryFailureDoesNotEscapeAfterCommitListener() {
        FriendshipNotificationRequested event = event();
        doThrow(new IllegalStateException("broker unavailable")).when(notifications).send(event);

        assertThatCode(() -> listener.afterCommit(event)).doesNotThrowAnyException();
    }

    private static FriendshipNotificationRequested event() {
        return new FriendshipNotificationRequested(FriendshipNotificationType.FRIEND_REQUEST_RECEIVED, 2,
                new SocialPlayerQueryPort.SafePlayerSummary(1, "Alpha", null), 7L, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
