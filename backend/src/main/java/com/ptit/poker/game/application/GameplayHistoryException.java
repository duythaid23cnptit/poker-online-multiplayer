package com.ptit.poker.game.application;

public final class GameplayHistoryException extends RuntimeException {
    private final String code;
    public GameplayHistoryException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
