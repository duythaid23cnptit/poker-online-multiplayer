package com.ptit.poker.social.chat.api.realtime;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.social.chat.application.ChatMessageService;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@Profile("!bootstrap")
public class ChatMessagingController {
    private final ChatMessageService messages;

    public ChatMessagingController(ChatMessageService messages) { this.messages = messages; }

    @MessageMapping("/room/{roomId}/chat")
    public void send(@DestinationVariable long roomId, ChatMessageCommand command, Principal principal) {
        AuthenticatedUser user = (AuthenticatedUser) ((Authentication) principal).getPrincipal();
        messages.sendMessage(user.userId(), roomId, command.clientMessageId(), command.content());
    }
}
