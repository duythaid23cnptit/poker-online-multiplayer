package com.ptit.poker.social.chat.application;

import com.ptit.poker.social.application.SocialPlayerQueryPort;
import com.ptit.poker.social.chat.domain.ChatMessageContent;
import com.ptit.poker.social.chat.domain.ClientMessageId;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Profile("!bootstrap")
public class ChatMessageService {
    public static final int MAX_HISTORY_LIMIT = 100;
    private final ChatMessagePersistencePort messages;
    private final ChatRoomAccessPort rooms;
    private final SocialPlayerQueryPort players;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ChatMessageService(ChatMessagePersistencePort messages, ChatRoomAccessPort rooms,
                              SocialPlayerQueryPort players, Clock clock, ApplicationEventPublisher events) {
        this.messages = messages;
        this.rooms = rooms;
        this.players = players;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public ChatMessageView sendMessage(long authenticatedUserId, long roomId,
                                       String clientMessageId, String content) {
        ClientMessageId command = parseClientMessageId(clientMessageId);
        ChatMessageContent normalized = parseContent(content);
        requireChatAccess(authenticatedUserId, roomId);

        String commandValue = command.toString();
        ChatMessageRecord record;
        boolean created = false;
        Instant createdAt = clock.instant();
        try {
            record = messages.insert(roomId, authenticatedUserId, commandValue, normalized.value(), createdAt);
            created = true;
        } catch (DuplicateKeyException duplicate) {
            if (!isClientCommandCollision(duplicate)) throw duplicate;
            record = messages.findByCommandKeyForUpdate(roomId, authenticatedUserId, commandValue)
                    .orElseThrow(() -> duplicate);
            if (!record.content().equals(normalized.value())) {
                throw ChatMessageException.conflict("Client message ID was already used with different content");
            }
        }
        ChatMessageView result = toView(record);
        if (created) events.publishEvent(new ChatMessageCreated(result));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageView> findRecentMessages(long authenticatedUserId, long roomId, int limit) {
        if (limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw ChatMessageException.bad("CHAT_INVALID_LIMIT", "Chat history limit must be between 1 and 100");
        }
        requireChatAccess(authenticatedUserId, roomId);
        List<ChatMessageRecord> records = messages.findRecentByRoomId(roomId, limit);
        Set<Long> senderIds = records.stream()
                .map(ChatMessageRecord::senderUserId)
                .collect(Collectors.toSet());
        Map<Long, SocialPlayerQueryPort.SafePlayerSummary> senders =
                players.findSafePlayerSummaries(senderIds);
        return records.stream().map(record -> toView(record, senders)).toList();
    }

    private void requireChatAccess(long authenticatedUserId, long roomId) {
        ChatRoomAccessPort.RoomAccess access = rooms.find(roomId, authenticatedUserId)
                .orElseThrow(() -> ChatMessageException.notFound("Room was not found"));
        if (!access.activeAccount()) {
            throw ChatMessageException.forbidden("CHAT_NOT_ROOM_MEMBER", "Active membership required");
        }
        if (access.closed()) {
            throw ChatMessageException.forbidden("CHAT_ROOM_CLOSED", "Room chat is closed");
        }
        if (!access.activeMember()) {
            throw ChatMessageException.forbidden("CHAT_NOT_ROOM_MEMBER", "Active membership required");
        }
    }

    private static boolean isClientCommandCollision(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != current) {
            if (current.getMessage() != null
                    && current.getMessage().contains("uk_chat_messages_client_command")) return true;
            current = current.getCause();
        }
        return current != null && current.getMessage() != null
                && current.getMessage().contains("uk_chat_messages_client_command");
    }

    private ChatMessageView toView(ChatMessageRecord record) {
        return toView(record, players.findSafePlayerSummaries(List.of(record.senderUserId())));
    }

    private ChatMessageView toView(
            ChatMessageRecord record,
            Map<Long, SocialPlayerQueryPort.SafePlayerSummary> senders) {
        SocialPlayerQueryPort.SafePlayerSummary sender = senders.get(record.senderUserId());
        if (sender == null) throw ChatMessageException.bad("CHAT_SENDER_NOT_FOUND", "Sender projection was not found");
        return new ChatMessageView(record.messageId(), record.roomId(), record.clientMessageId(),
                record.content(), record.createdAt(), sender);
    }

    private static ClientMessageId parseClientMessageId(String value) {
        try { return ClientMessageId.parse(value); }
        catch (RuntimeException failure) {
            throw ChatMessageException.bad("CHAT_INVALID_CLIENT_MESSAGE_ID", "Client message ID must be a UUID");
        }
    }

    private static ChatMessageContent parseContent(String value) {
        try { return new ChatMessageContent(value); }
        catch (RuntimeException failure) {
            throw ChatMessageException.bad("CHAT_INVALID_CONTENT", "Chat content is invalid");
        }
    }
}
