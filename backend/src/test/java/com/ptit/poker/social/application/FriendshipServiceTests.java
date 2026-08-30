package com.ptit.poker.social.application;

import com.ptit.poker.social.domain.FriendshipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class FriendshipServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");
    private FriendshipPersistencePort persistence;
    private SocialPlayerQueryPort players;
    private ApplicationEventPublisher events;
    private FriendshipService service;

    @BeforeEach
    void setUp() {
        persistence = mock(FriendshipPersistencePort.class);
        players = mock(SocialPlayerQueryPort.class);
        events = mock(ApplicationEventPublisher.class);
        when(players.findSafePlayerSummaries(any())).thenAnswer(invocation ->
                ((java.util.Collection<Long>) invocation.getArgument(0)).stream().collect(Collectors.toMap(
                        id -> id, id -> new SocialPlayerQueryPort.SafePlayerSummary(id, "Player " + id, null))));
        service = new FriendshipService(
                persistence, players, Clock.fixed(NOW, ZoneOffset.UTC), events);
    }

    @Test
    void selfRequestIsRejectedBeforePersistence() {
        assertCode(() -> service.sendFriendRequest(1, 1), "SELF_FRIEND_REQUEST");
        verify(persistence, never()).insertPending(anyLong(), anyLong(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void inactiveOrMissingRecipientIsConcealedAsPlayerNotFound() {
        when(players.findActivePlayer(2)).thenReturn(Optional.empty());
        assertCode(() -> service.sendFriendRequest(1, 2), "PLAYER_NOT_FOUND");
    }

    @Test
    void newPairCreatesPendingRequest() {
        player(2, "Beta");
        FriendshipRecord record = record(7, 1, 2, FriendshipStatus.PENDING, null, 0);
        when(persistence.insertPending(1, 2, NOW)).thenReturn(true);
        when(persistence.findCanonicalPairForUpdate(1, 2)).thenReturn(Optional.of(record));

        FriendshipSendResult result = service.sendFriendRequest(1, 2);

        assertThat(result.created()).isTrue();
        assertThat(result.friendship().status()).isEqualTo(FriendshipStatus.PENDING);
        verifyNotification(FriendshipNotificationType.FRIEND_REQUEST_RECEIVED, 2, 1, 7L);
    }

    @Test
    void sameDirectionPendingRequestIsConflict() {
        player(2, "Beta");
        when(persistence.findCanonicalPairForUpdate(1, 2))
                .thenReturn(Optional.of(record(7, 1, 2, FriendshipStatus.PENDING, null, 0)));
        assertCode(() -> service.sendFriendRequest(1, 2), "FRIEND_REQUEST_ALREADY_EXISTS");
    }

    @Test
    void crossedPendingRequestAutoAccepts() {
        player(1, "Alpha");
        FriendshipRecord pending = record(7, 1, 2, FriendshipStatus.PENDING, null, 0);
        FriendshipRecord accepted = record(7, 1, 2, FriendshipStatus.ACCEPTED, NOW, 1);
        when(persistence.findCanonicalPairForUpdate(2, 1)).thenReturn(Optional.of(pending));
        when(persistence.transition(7, FriendshipStatus.ACCEPTED, NOW)).thenReturn(accepted);

        FriendshipSendResult result = service.sendFriendRequest(2, 1);

        assertThat(result.created()).isFalse();
        assertThat(result.friendship().status()).isEqualTo(FriendshipStatus.ACCEPTED);
        verifyNotification(FriendshipNotificationType.FRIEND_REQUEST_ACCEPTED, 1, 2, 7L);
    }

    @Test
    void acceptedPairRejectsAnotherRequest() {
        player(2, "Beta");
        when(persistence.findCanonicalPairForUpdate(1, 2))
                .thenReturn(Optional.of(record(7, 1, 2, FriendshipStatus.ACCEPTED, NOW, 1)));
        assertCode(() -> service.sendFriendRequest(1, 2), "FRIENDSHIP_ALREADY_EXISTS");
    }

    @Test
    void rejectedPairIsReopenedWithNewDirection() {
        player(1, "Alpha");
        FriendshipRecord rejected = record(7, 1, 2, FriendshipStatus.REJECTED, NOW.minusSeconds(1), 1);
        FriendshipRecord reopened = record(7, 2, 1, FriendshipStatus.PENDING, null, 2);
        when(persistence.findCanonicalPairForUpdate(2, 1)).thenReturn(Optional.of(rejected));
        when(persistence.reopen(7, 2, 1, NOW)).thenReturn(reopened);

        assertThat(service.sendFriendRequest(2, 1).friendship().status()).isEqualTo(FriendshipStatus.PENDING);
        verifyNotification(FriendshipNotificationType.FRIEND_REQUEST_RECEIVED, 1, 2, 7L);
    }

    @Test
    void onlyRecipientMayAccept() {
        when(persistence.findRequestByIdForUpdate(7))
                .thenReturn(Optional.of(record(7, 1, 2, FriendshipStatus.PENDING, null, 0)));
        assertCode(() -> service.acceptFriendRequest(1, 7), "FRIEND_REQUEST_NOT_AUTHORIZED");
    }

    @Test
    void completedRequestCannotBeRespondedToAgain() {
        when(persistence.findRequestByIdForUpdate(7))
                .thenReturn(Optional.of(record(7, 1, 2, FriendshipStatus.ACCEPTED, NOW, 1)));
        assertCode(() -> service.rejectFriendRequest(2, 7), "FRIEND_REQUEST_NOT_PENDING");
    }

    @Test
    void missingAcceptedPairCannotBeRemoved() {
        when(persistence.deleteAcceptedPair(1, 2)).thenReturn(false);
        assertCode(() -> service.removeFriend(1, 2), "FRIENDSHIP_NOT_FOUND");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void manualAcceptNotifiesOriginalRequesterWithRecipientAsActor() {
        FriendshipRecord pending = record(7, 1, 2, FriendshipStatus.PENDING, null, 0);
        FriendshipRecord accepted = record(7, 1, 2, FriendshipStatus.ACCEPTED, NOW, 1);
        when(persistence.findRequestByIdForUpdate(7)).thenReturn(Optional.of(pending));
        when(persistence.transition(7, FriendshipStatus.ACCEPTED, NOW)).thenReturn(accepted);

        service.acceptFriendRequest(2, 7);

        verifyNotification(FriendshipNotificationType.FRIEND_REQUEST_ACCEPTED, 1, 2, 7L);
    }

    @Test
    void manualRejectNotifiesOriginalRequesterWithRecipientAsActor() {
        FriendshipRecord pending = record(7, 1, 2, FriendshipStatus.PENDING, null, 0);
        FriendshipRecord rejected = record(7, 1, 2, FriendshipStatus.REJECTED, NOW, 1);
        when(persistence.findRequestByIdForUpdate(7)).thenReturn(Optional.of(pending));
        when(persistence.transition(7, FriendshipStatus.REJECTED, NOW)).thenReturn(rejected);

        service.rejectFriendRequest(2, 7);

        verifyNotification(FriendshipNotificationType.FRIEND_REQUEST_REJECTED, 1, 2, 7L);
    }

    @Test
    void successfulRemoveCapturesActorBeforeDeletionAndNotifiesOtherUser() {
        when(persistence.deleteAcceptedPair(1, 2)).thenReturn(true);

        service.removeFriend(1, 2);

        verifyNotification(FriendshipNotificationType.FRIEND_REMOVED, 2, 1, null);
    }

    @Test
    void requestDirectionIsStrictAndCaseSensitive() {
        assertThat(FriendRequestDirection.parse("incoming")).isEqualTo(FriendRequestDirection.INCOMING);
        assertCode(() -> FriendRequestDirection.parse("INCOMING"), "INVALID_REQUEST");
    }

    private void player(long id, String name) {
        var summary = new SocialPlayerQueryPort.SafePlayerSummary(id, name, null);
        when(players.findActivePlayer(id)).thenReturn(Optional.of(summary));
    }

    private static FriendshipRecord record(long id, long requester, long recipient, FriendshipStatus status,
                                            Instant respondedAt, long version) {
        return new FriendshipRecord(id, requester, recipient, status, NOW.minusSeconds(60), respondedAt, version);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOf(FriendshipException.class)
                .extracting(failure -> ((FriendshipException) failure).code()).isEqualTo(code);
    }

    private void verifyNotification(FriendshipNotificationType type, long recipient, long actor, Long requestId) {
        var captor = org.mockito.ArgumentCaptor.forClass(FriendshipNotificationRequested.class);
        verify(events, times(1)).publishEvent(captor.capture());
        FriendshipNotificationRequested notification = captor.getValue();
        assertThat(notification.type()).isEqualTo(type);
        assertThat(notification.recipientUserId()).isEqualTo(recipient);
        assertThat(notification.actor().userId()).isEqualTo(actor);
        assertThat(notification.requestId()).isEqualTo(requestId);
        assertThat(notification.occurredAt()).isEqualTo(NOW);
    }
}
