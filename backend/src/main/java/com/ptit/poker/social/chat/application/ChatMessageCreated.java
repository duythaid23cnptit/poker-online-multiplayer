package com.ptit.poker.social.chat.application;

import java.util.Objects;

public record ChatMessageCreated(ChatMessageView message) {
    public ChatMessageCreated { Objects.requireNonNull(message); }
}
