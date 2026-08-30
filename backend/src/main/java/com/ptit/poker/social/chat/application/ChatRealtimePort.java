package com.ptit.poker.social.chat.application;

public interface ChatRealtimePort {
    void broadcast(ChatMessageView message);
}
