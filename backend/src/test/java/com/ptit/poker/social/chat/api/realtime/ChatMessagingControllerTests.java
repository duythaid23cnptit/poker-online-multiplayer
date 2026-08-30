package com.ptit.poker.social.chat.api.realtime;

import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.social.chat.application.ChatMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatMessagingControllerTests {
    private final ChatMessageService service = mock(ChatMessageService.class);
    private final ChatMessagingController controller = new ChatMessagingController(service);

    @Test void delegatesDestinationRoomAndAuthenticatedUser() {
        var principal = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(7L, "alpha", Role.PLAYER), null, List.of());
        ChatMessageCommand command = new ChatMessageCommand("command-id", "hello");

        controller.send(12, command, principal);

        verify(service).sendMessage(7, 12, "command-id", "hello");
    }

    @Test void commandHasOnlyFrozenClientControlledFields() {
        assertThat(ChatMessageCommand.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("clientMessageId", "content");
    }
}
