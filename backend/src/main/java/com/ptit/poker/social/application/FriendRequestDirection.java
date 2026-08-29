package com.ptit.poker.social.application;

public enum FriendRequestDirection {
    INCOMING,
    OUTGOING;

    public static FriendRequestDirection parse(String value) {
        if (value == null) throw FriendshipException.badRequest("Direction is required");
        return switch (value) {
            case "incoming" -> INCOMING;
            case "outgoing" -> OUTGOING;
            default -> throw FriendshipException.badRequest("Direction must be incoming or outgoing");
        };
    }
}
