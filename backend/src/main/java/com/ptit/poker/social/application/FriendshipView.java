package com.ptit.poker.social.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.social.domain.FriendshipStatus;

import java.time.Instant;

public record FriendshipView(
        long requestId,
        FriendshipStatus status,
        Instant createdAt,
        Instant respondedAt,
        SocialPlayerQueryPort.SafePlayerSummary otherPlayer,
        @JsonInclude(JsonInclude.Include.NON_NULL) PresenceStatus presenceStatus) {
}
