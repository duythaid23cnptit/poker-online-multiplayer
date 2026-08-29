package com.ptit.poker.admin.application;

import org.springframework.http.HttpStatus;

public final class AdminModerationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public AdminModerationException(String code, String message, HttpStatus status) {
        super(message); this.code = code; this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
