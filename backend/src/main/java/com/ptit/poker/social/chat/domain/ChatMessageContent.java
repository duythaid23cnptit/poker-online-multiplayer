package com.ptit.poker.social.chat.domain;

import java.util.Objects;

public record ChatMessageContent(String value) {
    public static final int MAX_CODE_POINTS = 500;

    public ChatMessageContent {
        Objects.requireNonNull(value, "Chat message content is required");
        value = value.strip();
        if (value.isEmpty()) throw new IllegalArgumentException("Chat message content must not be blank");
        if (value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("Chat message content must not exceed 500 Unicode code points");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Chat message content must not contain control characters");
        }
    }
}
