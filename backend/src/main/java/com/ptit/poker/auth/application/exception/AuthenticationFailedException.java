package com.ptit.poker.auth.application.exception;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Authentication failed");
    }
}

