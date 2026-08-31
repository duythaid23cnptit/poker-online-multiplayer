package com.ptit.poker.social.presence.application;

import com.ptit.poker.player.domain.PresenceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendPresenceNotificationListenerTests {
    private final AcceptedFriendQueryPort friends = mock(AcceptedFriendQueryPort.class);
    private final FriendPresenceNotificationPort notifications = mock(FriendPresenceNotificationPort.class);
    private final FriendPresenceNotificationListener listener =
            new FriendPresenceNotificationListener(friends, notifications);

    @Test void notifiesOnlyIdsReturnedByAcceptedFriendQuery() {
        PresenceChanged changed = new PresenceChanged(7, PresenceStatus.ONLINE);
        when(friends.findAcceptedFriendIds(7)).thenReturn(List.of(10L, 11L));
        listener.afterCommit(changed);
        verify(notifications).notifyFriend(10, changed);
        verify(notifications).notifyFriend(11, changed);
    }

    @Test void oneDeliveryFailureDoesNotPreventOtherFriendsOrEscape() {
        PresenceChanged changed = new PresenceChanged(7, PresenceStatus.OFFLINE);
        when(friends.findAcceptedFriendIds(7)).thenReturn(List.of(10L, 11L));
        doThrow(new IllegalStateException("broker unavailable")).when(notifications).notifyFriend(10, changed);
        assertThatCode(() -> listener.afterCommit(changed)).doesNotThrowAnyException();
        verify(notifications).notifyFriend(11, changed);
    }

    @Test void recipientLookupFailureDoesNotEscapeAfterCommit() {
        PresenceChanged changed = new PresenceChanged(7, PresenceStatus.ONLINE);
        when(friends.findAcceptedFriendIds(7)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatCode(() -> listener.afterCommit(changed)).doesNotThrowAnyException();
    }
}
