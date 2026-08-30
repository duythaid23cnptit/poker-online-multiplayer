package com.ptit.poker.social.chat.infrastructure.realtime;

import com.ptit.poker.social.chat.api.realtime.ChatRealtimeEvent;
import com.ptit.poker.social.chat.application.ChatMessageView;
import com.ptit.poker.social.chat.application.ChatRealtimePort;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
@Profile("!bootstrap")
class StompChatRealtimeAdapter implements ChatRealtimePort {
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    StompChatRealtimeAdapter(SimpMessagingTemplate messaging, Clock clock) {
        this.messaging = messaging; this.clock = clock;
    }

    @Override
    public void broadcast(ChatMessageView message) {
        ChatRealtimeEvent event = new ChatRealtimeEvent(1, UUID.randomUUID(),
                ChatRealtimeEvent.ChatEventType.CHAT_MESSAGE, clock.instant(),
                new ChatRealtimeEvent.Scope(message.roomId()), message);
        messaging.convertAndSend("/topic/room/" + message.roomId(), event);
    }
}
