package com.ptit.poker.social.chat.api;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.social.chat.application.ChatMessageException;
import com.ptit.poker.social.chat.application.ChatMessageService;
import com.ptit.poker.social.chat.application.ChatMessageView;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/rooms/{roomId}/chat")
public class ChatHistoryController {
    private final ChatMessageService chatMessages;

    public ChatHistoryController(ChatMessageService chatMessages) {
        this.chatMessages = chatMessages;
    }

    @GetMapping("/messages")
    public List<ChatMessageView> recentMessages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long roomId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return chatMessages.findRecentMessages(user.userId(), roomId, limit);
        } catch (ChatMessageException failure) {
            throw new ResponseStatusException(statusFor(failure.code()), failure.getMessage(), failure);
        }
    }

    private static HttpStatus statusFor(String code) {
        return switch (code) {
            case "CHAT_ROOM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CHAT_NOT_ROOM_MEMBER", "CHAT_ROOM_CLOSED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
