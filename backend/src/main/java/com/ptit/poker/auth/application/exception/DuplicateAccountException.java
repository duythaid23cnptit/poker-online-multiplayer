package com.ptit.poker.auth.application.exception;

public class DuplicateAccountException extends RuntimeException {
    private final String field;

    public DuplicateAccountException(String field) {
        super("An account with that " + field + " already exists");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

