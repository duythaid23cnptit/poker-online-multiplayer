package com.ptit.poker.social.application;

import org.springframework.http.HttpStatus;

public final class FriendshipException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    private FriendshipException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }

    static FriendshipException conflict(String code, String message) {
        return new FriendshipException(code, HttpStatus.CONFLICT, message);
    }

    static FriendshipException notFound(String code, String message) {
        return new FriendshipException(code, HttpStatus.NOT_FOUND, message);
    }

    static FriendshipException forbidden(String code, String message) {
        return new FriendshipException(code, HttpStatus.FORBIDDEN, message);
    }

    static FriendshipException badRequest(String message) {
        return new FriendshipException("INVALID_REQUEST", HttpStatus.BAD_REQUEST, message);
    }

    static FriendshipException internalPersistence() {
        return new FriendshipException("FRIENDSHIP_PERSISTENCE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                "Friendship state could not be persisted");
    }
}
