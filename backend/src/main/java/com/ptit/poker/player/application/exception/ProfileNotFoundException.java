package com.ptit.poker.player.application.exception;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException() {
        super("Player profile was not found");
    }
}

