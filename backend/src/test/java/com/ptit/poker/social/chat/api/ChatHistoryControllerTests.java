package com.ptit.poker.social.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.social.application.SocialPlayerQueryPort;
import com.ptit.poker.social.chat.application.ChatMessageException;
import com.ptit.poker.social.chat.application.ChatMessageService;
import com.ptit.poker.social.chat.application.ChatMessageView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryControllerTests {
    private final ChatMessageService service = mock(ChatMessageService.class);
    private final ChatHistoryController controller = new ChatHistoryController(service);
    private final AuthenticatedUser user = new AuthenticatedUser(7L, "alpha", Role.PLAYER);

    @Test
    void delegatesAuthenticatedUserRoomAndLimitAndReturnsSafeShape() throws Exception {
        ChatMessageView message = new ChatMessageView(9, 12, "command", "hello",
                Instant.parse("2026-09-04T01:02:03Z"),
                new SocialPlayerQueryPort.SafePlayerSummary(7, "Alpha", "/avatar.png"));
        when(service.findRecentMessages(7, 12, 25)).thenReturn(List.of(message));

        assertThat(controller.recentMessages(user, 12, 25)).containsExactly(message);
        verify(service).findRecentMessages(7, 12, 25);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(message);
        assertThat(json).contains("messageId", "roomId", "clientMessageId", "content", "createdAt",
                "sender", "displayName", "avatarUrl")
                .doesNotContain("email", "password", "accountChips");
    }

    @Test
    void mapsApplicationAccessAndValidationFailuresToHttpSemantics() {
        when(service.findRecentMessages(7, 12, 50))
                .thenThrow(ChatMessageException.forbidden("CHAT_NOT_ROOM_MEMBER", "forbidden"));
        assertStatus(() -> controller.recentMessages(user, 12, 50), HttpStatus.FORBIDDEN);

        when(service.findRecentMessages(7, 404, 50))
                .thenThrow(ChatMessageException.notFound("missing"));
        assertStatus(() -> controller.recentMessages(user, 404, 50), HttpStatus.NOT_FOUND);

        when(service.findRecentMessages(7, 12, 101))
                .thenThrow(ChatMessageException.bad("CHAT_INVALID_LIMIT", "invalid"));
        assertStatus(() -> controller.recentMessages(user, 12, 101), HttpStatus.BAD_REQUEST);
    }

    @Test
    void endpointDeclaresBoundedDefaultLimit() throws Exception {
        Method endpoint = ChatHistoryController.class.getDeclaredMethod(
                "recentMessages", AuthenticatedUser.class, long.class, int.class);
        RequestParam limit = endpoint.getParameters()[2].getAnnotation(RequestParam.class);

        assertThat(limit.defaultValue()).isEqualTo("50");
        assertThat(ChatMessageService.MAX_HISTORY_LIMIT).isEqualTo(100);
    }

    private static void assertStatus(ThrowingCall call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(status));
    }

    @FunctionalInterface
    private interface ThrowingCall { void run(); }
}
