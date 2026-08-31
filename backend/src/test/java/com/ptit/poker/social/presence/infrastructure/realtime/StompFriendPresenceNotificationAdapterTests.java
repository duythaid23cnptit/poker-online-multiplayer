package com.ptit.poker.social.presence.infrastructure.realtime;

import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.social.presence.api.realtime.FriendPresenceNotificationEvent;
import com.ptit.poker.social.presence.application.PresenceChanged;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompFriendPresenceNotificationAdapterTests {
    @Test
    void sendsVersionedSafeEventToRecipientsPrivateNotificationQueue() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant occurredAt = Instant.parse("2026-08-30T10:00:00Z");
        when(jdbc.queryForObject("SELECT username FROM users WHERE id=?", String.class, 12L))
                .thenReturn("accepted-friend");
        var adapter = new StompFriendPresenceNotificationAdapter(
                messaging, jdbc, Clock.fixed(occurredAt, ZoneOffset.UTC));

        adapter.notifyFriend(12, new PresenceChanged(7, PresenceStatus.ONLINE));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("accepted-friend"),
                org.mockito.ArgumentMatchers.eq("/queue/notifications"), eventCaptor.capture());
        FriendPresenceNotificationEvent event = (FriendPresenceNotificationEvent) eventCaptor.getValue();
        assertThat(event.protocolVersion()).isOne();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.type()).isEqualTo(FriendPresenceNotificationEvent.EventType.FRIEND_STATUS_CHANGED);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.payload().userId()).isEqualTo(7);
        assertThat(event.payload().presenceStatus()).isEqualTo(PresenceStatus.ONLINE);
    }
}
