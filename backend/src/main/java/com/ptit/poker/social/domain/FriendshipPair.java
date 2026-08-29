package com.ptit.poker.social.domain;

public record FriendshipPair(long lowerUserId, long higherUserId) {

    public FriendshipPair {
        if (lowerUserId <= 0 || higherUserId <= 0) {
            throw new IllegalArgumentException("Friendship user IDs must be positive");
        }
        if (lowerUserId >= higherUserId) {
            throw new IllegalArgumentException("Friendship pair must contain two distinct, canonically ordered users");
        }
    }

    public static FriendshipPair of(long userA, long userB) {
        if (userA == userB) {
            throw new IllegalArgumentException("A user cannot befriend themselves");
        }
        return new FriendshipPair(Math.min(userA, userB), Math.max(userA, userB));
    }
}
