package com.ptit.poker.game.application.runtime;

public final class GameRuntimeException extends RuntimeException {
    private final String code;
    public GameRuntimeException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
