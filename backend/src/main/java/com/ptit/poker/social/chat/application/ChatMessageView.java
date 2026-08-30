package com.ptit.poker.social.chat.application;

import com.ptit.poker.social.application.SocialPlayerQueryPort;

import java.time.Instant;

public record ChatMessageView(long messageId, long roomId, String clientMessageId,
                              String content, Instant createdAt,
                              SocialPlayerQueryPort.SafePlayerSummary sender) { }
