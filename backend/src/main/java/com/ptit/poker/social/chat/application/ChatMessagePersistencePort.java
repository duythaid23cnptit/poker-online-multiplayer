package com.ptit.poker.social.chat.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessagePersistencePort {
    ChatMessageRecord insert(long roomId, long senderUserId, String clientMessageId,
                             String content, Instant createdAt);

    Optional<ChatMessageRecord> findByCommandKeyForUpdate(long roomId, long senderUserId, String clientMessageId);

    List<ChatMessageRecord> findRecentByRoomId(long roomId, int limit);
}
