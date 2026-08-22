package com.ptit.poker.auth.application.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Account is locked");
    }
}

