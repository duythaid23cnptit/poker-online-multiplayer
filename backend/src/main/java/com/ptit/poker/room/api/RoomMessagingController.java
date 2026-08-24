package com.ptit.poker.room.api;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.room.api.dto.ReadyCommand;
import com.ptit.poker.room.application.RoomApplicationService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@Profile("!bootstrap")
public class RoomMessagingController {
    private final RoomApplicationService rooms;

    public RoomMessagingController(RoomApplicationService rooms) { this.rooms = rooms; }

    @MessageMapping("/room/{roomId}/ready")
    public void ready(@DestinationVariable Long roomId, @Valid ReadyCommand command, Principal principal) {
        AuthenticatedUser user = (AuthenticatedUser) ((Authentication) principal).getPrincipal();
        rooms.setReady(user.userId(), roomId, command.ready());
    }
}
