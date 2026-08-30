package com.ptit.poker.social.chat.application;

import java.time.Instant;

public record ChatMessageRecord(long messageId, long roomId, long senderUserId,
                                String clientMessageId, String content, Instant createdAt) { }
