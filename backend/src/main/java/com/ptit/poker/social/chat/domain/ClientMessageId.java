package com.ptit.poker.social.chat.domain;

import java.util.Objects;
import java.util.UUID;

public record ClientMessageId(UUID value) {
    public ClientMessageId { Objects.requireNonNull(value, "Client message ID is required"); }

    public static ClientMessageId parse(String value) {
        Objects.requireNonNull(value, "Client message ID is required");
        return new ClientMessageId(UUID.fromString(value));
    }

    @Override public String toString() { return value.toString(); }
}
