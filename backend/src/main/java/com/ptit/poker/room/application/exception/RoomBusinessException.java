package com.ptit.poker.room.application.exception;

import org.springframework.http.HttpStatus;

public class RoomBusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public RoomBusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
