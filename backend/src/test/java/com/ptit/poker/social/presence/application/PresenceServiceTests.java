package com.ptit.poker.social.presence.application;

import com.ptit.poker.player.domain.PresenceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceServiceTests {
    private PresencePersistencePort persistence;
    private ApplicationEventPublisher events;
    private PresenceService service;

    @BeforeEach void setUp() {
        persistence = mock(PresencePersistencePort.class); events = mock(ApplicationEventPublisher.class);
        service = new PresenceService(persistence, events);
    }

    @Test void offlineToOnlinePersistsAndPublishesOnce() {
        when(persistence.transition(7, PresenceStatus.ONLINE)).thenReturn(true);
        assertThat(service.online(7)).isTrue();
        verify(persistence).transition(7, PresenceStatus.ONLINE);
        verify(events).publishEvent(new PresenceChanged(7, PresenceStatus.ONLINE));
    }

    @Test void duplicateOnlineDoesNotPublish() {
        assertThat(service.online(7)).isFalse();
        verify(persistence).transition(7, PresenceStatus.ONLINE);
        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test void onlineToOfflinePersistsAndPublishesOnce() {
        when(persistence.transition(7, PresenceStatus.OFFLINE)).thenReturn(true);
        assertThat(service.offline(7)).isTrue();
        verify(persistence).transition(7, PresenceStatus.OFFLINE);
        verify(events).publishEvent(new PresenceChanged(7, PresenceStatus.OFFLINE));
    }

    @Test void duplicateOfflineDoesNotPublish() {
        assertThat(service.offline(7)).isFalse();
        verify(persistence).transition(7, PresenceStatus.OFFLINE);
        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
