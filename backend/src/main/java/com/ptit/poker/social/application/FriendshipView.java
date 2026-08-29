package com.ptit.poker.social.application;

import com.ptit.poker.social.domain.FriendshipStatus;

import java.time.Instant;

public record FriendshipView(
        long requestId,
        FriendshipStatus status,
        Instant createdAt,
        Instant respondedAt,
        SocialPlayerQueryPort.SafePlayerSummary otherPlayer) {
}
