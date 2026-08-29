package com.ptit.poker.social.application;

import com.ptit.poker.social.domain.FriendshipStatus;

import java.time.Instant;

public record FriendshipRecord(
        long id,
        long requesterUserId,
        long recipientUserId,
        FriendshipStatus status,
        Instant createdAt,
        Instant respondedAt,
        long version) {

    public long otherUserId(long currentUserId) {
        if (requesterUserId == currentUserId) return recipientUserId;
        if (recipientUserId == currentUserId) return requesterUserId;
        throw new IllegalArgumentException("User is not a friendship participant");
    }
}
