package com.ptit.poker.social.chat.application;

import com.ptit.poker.social.application.SocialPlayerQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-30T01:02:03Z");
    private ChatMessagePersistencePort messages;
    private ChatRoomAccessPort rooms;
    private SocialPlayerQueryPort players;
    private ChatMessageService service;
    private ApplicationEventPublisher events;
    private final UUID command = UUID.randomUUID();
    private final SocialPlayerQueryPort.SafePlayerSummary sender =
            new SocialPlayerQueryPort.SafePlayerSummary(7, "Alpha", "/avatar.png");

    @BeforeEach
    void setUp() {
        messages = mock(ChatMessagePersistencePort.class);
        rooms = mock(ChatRoomAccessPort.class);
        players = mock(SocialPlayerQueryPort.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ChatMessageService(messages, rooms, players, Clock.fixed(NOW, ZoneOffset.UTC), events);
        when(rooms.find(12, 7)).thenReturn(Optional.of(new ChatRoomAccessPort.RoomAccess(
                12, false, true, true)));
        when(players.findSafePlayerSummaries(any())).thenReturn(Map.of(7L, sender));
    }

    @Test
    void memberMessageIsNormalizedPersistedAndMappedSafely() {
        ChatMessageRecord record = record("Hello world");
        when(messages.insert(12, 7, command.toString(), "Hello world", NOW)).thenReturn(record);

        ChatMessageView result = service.sendMessage(7, 12, command.toString(), "  Hello world  ");

        assertThat(result).isEqualTo(new ChatMessageView(99, 12, command.toString(), "Hello world", NOW, sender));
        verify(events).publishEvent(new ChatMessageCreated(result));
    }

    @Test
    void spectatorIsAllowed() {
        when(rooms.find(12, 7)).thenReturn(Optional.of(new ChatRoomAccessPort.RoomAccess(
                12, false, true, true)));
        when(messages.insert(12, 7, command.toString(), "hello", NOW)).thenReturn(record("hello"));

        assertThat(service.sendMessage(7, 12, command.toString(), "hello").messageId()).isEqualTo(99);
    }

    @Test
    void unknownRoomIsRejectedBeforePersistence() {
        when(rooms.find(404, 7)).thenReturn(Optional.empty());

        assertCode(() -> service.sendMessage(7, 404, command.toString(), "hello"), "CHAT_ROOM_NOT_FOUND");
        verify(messages, never()).insert(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void inactiveMemberAndClosedRoomAreRejected() {
        when(rooms.find(12, 7)).thenReturn(Optional.of(new ChatRoomAccessPort.RoomAccess(
                12, false, false, true)));
        assertCode(() -> service.sendMessage(7, 12, command.toString(), "hello"), "CHAT_NOT_ROOM_MEMBER");

        when(rooms.find(12, 7)).thenReturn(Optional.of(new ChatRoomAccessPort.RoomAccess(
                12, true, true, true)));
        assertCode(() -> service.sendMessage(7, 12, command.toString(), "hello"), "CHAT_ROOM_CLOSED");
    }

    @Test
    void inactiveAccountCannotSend() {
        when(rooms.find(12, 7)).thenReturn(Optional.of(new ChatRoomAccessPort.RoomAccess(
                12, false, true, false)));
        assertCode(() -> service.sendMessage(7, 12, command.toString(), "hello"), "CHAT_NOT_ROOM_MEMBER");
    }

    @Test
    void invalidContentAndClientIdAreRejectedBeforePersistence() {
        assertCode(() -> service.sendMessage(7, 12, command.toString(), "  \n"), "CHAT_INVALID_CONTENT");
        assertCode(() -> service.sendMessage(7, 12, "bad", "hello"), "CHAT_INVALID_CLIENT_MESSAGE_ID");
        verify(messages, never()).insert(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void sameRetryReturnsExistingMessage() {
        ChatMessageRecord existing = record("Hello");
        when(messages.insert(12, 7, command.toString(), "Hello", NOW))
                .thenThrow(duplicate());
        when(messages.findByCommandKeyForUpdate(12, 7, command.toString())).thenReturn(Optional.of(existing));

        assertThat(service.sendMessage(7, 12, command.toString(), " Hello ").messageId()).isEqualTo(99);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void conflictingRetryIsRejectedAndOriginalIsUntouched() {
        when(messages.insert(12, 7, command.toString(), "Changed", NOW))
                .thenThrow(duplicate());
        when(messages.findByCommandKeyForUpdate(12, 7, command.toString())).thenReturn(Optional.of(record("Original")));

        assertCode(() -> service.sendMessage(7, 12, command.toString(), "Changed"), "CHAT_CLIENT_MESSAGE_ID_CONFLICT");
    }

    @Test
    void unrelatedIntegrityFailureIsNotHidden() {
        RuntimeException failure = new org.springframework.dao.DataIntegrityViolationException("foreign key");
        when(messages.insert(12, 7, command.toString(), "hello", NOW)).thenThrow(failure);

        assertThatThrownBy(() -> service.sendMessage(7, 12, command.toString(), "hello")).isSameAs(failure);
    }

    @Test
    void duplicateFromAnotherConstraintIsNotTreatedAsIdempotency() {
        RuntimeException failure = new org.springframework.dao.DuplicateKeyException("other_unique_constraint");
        when(messages.insert(12, 7, command.toString(), "hello", NOW)).thenThrow(failure);

        assertThatThrownBy(() -> service.sendMessage(7, 12, command.toString(), "hello")).isSameAs(failure);
        verify(messages, never()).findByCommandKeyForUpdate(anyLong(), anyLong(), anyString());
    }

    @Test
    void activeMemberHistoryIsMappedChronologicallyWithOneBatchedSenderLookup() {
        UUID secondCommand = UUID.randomUUID();
        SocialPlayerQueryPort.SafePlayerSummary secondSender =
                new SocialPlayerQueryPort.SafePlayerSummary(8, "Bravo", null);
        ChatMessageRecord first = record("first");
        ChatMessageRecord second = new ChatMessageRecord(
                100, 12, 8, secondCommand.toString(), "second", NOW.plusSeconds(1));
        when(messages.findRecentByRoomId(12, 50)).thenReturn(List.of(first, second));
        when(players.findSafePlayerSummaries(any())).thenReturn(Map.of(7L, sender, 8L, secondSender));

        List<ChatMessageView> history = service.findRecentMessages(7, 12, 50);

        assertThat(history).extracting(ChatMessageView::messageId).containsExactly(99L, 100L);
        assertThat(history).extracting(view -> view.sender().displayName())
                .containsExactly("Alpha", "Bravo");
        verify(players).findSafePlayerSummaries(java.util.Set.of(7L, 8L));
    }

    @Test
    void spectatorHistoryUsesTheSameActiveMembershipContract() {
        when(messages.findRecentByRoomId(12, 1)).thenReturn(List.of(record("hello")));

        assertThat(service.findRecentMessages(7, 12, 1)).hasSize(1);
    }

    @Test
    void maximumHistoryLimitIsAcceptedAndPassedAsABoundParameter() {
        when(messages.findRecentByRoomId(12, 100)).thenReturn(List.of());

        assertThat(service.findRecentMessages(7, 12, 100)).isEmpty();
        verify(messages).findRecentByRoomId(12, 100);
    }

    @Test
    void historyRejectsOutsiderInactiveAccountClosedAndUnknownRoom() {
        when(rooms.find(12, 7)).thenReturn(Optional.of(new ChatRoomAccessPort.RoomAccess(
                12, false, false, true)));
        assertCode(() -> service.findRecentMessages(7, 12, 50), "CHAT_NOT_ROOM_MEMBER");

        when(rooms.find(12, 7)).thenReturn(Optional.of(new ChatRoomAccessPort.RoomAccess(
                12, false, true, false)));
        assertCode(() -> service.findRecentMessages(7, 12, 50), "CHAT_NOT_ROOM_MEMBER");

        when(rooms.find(12, 7)).thenReturn(Optional.of(new ChatRoomAccessPort.RoomAccess(
                12, true, true, true)));
        assertCode(() -> service.findRecentMessages(7, 12, 50), "CHAT_ROOM_CLOSED");

        when(rooms.find(404, 7)).thenReturn(Optional.empty());
        assertCode(() -> service.findRecentMessages(7, 404, 50), "CHAT_ROOM_NOT_FOUND");
        verify(messages, never()).findRecentByRoomId(anyLong(), anyInt());
    }

    @Test
    void historyRejectsInvalidLimitsBeforeAccessOrPersistence() {
        assertCode(() -> service.findRecentMessages(7, 12, 0), "CHAT_INVALID_LIMIT");
        assertCode(() -> service.findRecentMessages(7, 12, 101), "CHAT_INVALID_LIMIT");
        verify(rooms, never()).find(anyLong(), anyLong());
        verify(messages, never()).findRecentByRoomId(anyLong(), anyInt());
    }

    @Test
    void historyRejectsMissingSafeSenderProjection() {
        when(messages.findRecentByRoomId(12, 50)).thenReturn(List.of(record("hello")));
        when(players.findSafePlayerSummaries(any())).thenReturn(Map.of());

        assertCode(() -> service.findRecentMessages(7, 12, 50), "CHAT_SENDER_NOT_FOUND");
    }

    private ChatMessageRecord record(String content) {
        return new ChatMessageRecord(99, 12, 7, command.toString(), content, NOW);
    }

    private static org.springframework.dao.DuplicateKeyException duplicate() {
        return new org.springframework.dao.DuplicateKeyException(
                "Duplicate entry for key 'chat_messages.uk_chat_messages_client_command'");
    }

    private static void assertCode(ThrowingCall call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ChatMessageException.class)
                .extracting(failure -> ((ChatMessageException) failure).code()).isEqualTo(code);
    }

    @FunctionalInterface private interface ThrowingCall { void run(); }
}
