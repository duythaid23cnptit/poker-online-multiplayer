package com.ptit.poker.social.application;

import java.time.Instant;
import java.util.Objects;

public record FriendshipNotificationRequested(
        FriendshipNotificationType type,
        long recipientUserId,
        SocialPlayerQueryPort.SafePlayerSummary actor,
        Long requestId,
        Instant occurredAt) {

    public FriendshipNotificationRequested {
        Objects.requireNonNull(type);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(occurredAt);
        if (recipientUserId <= 0) throw new IllegalArgumentException("Notification recipient must be positive");
    }
}
