package com.ptit.poker.room.application;

import com.ptit.poker.room.api.event.RealtimeEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!bootstrap")
public class RoomRealtimePublisher {
    private final SimpMessagingTemplate messaging;

    public RoomRealtimePublisher(SimpMessagingTemplate messaging) { this.messaging = messaging; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(RoomChangedEvent changed) {
        if (changed.roomType() != null) {
            messaging.convertAndSend("/topic/room/" + changed.roomId(),
                    RealtimeEvent.room(changed.roomType(), changed.roomId(), changed.roomPayload()));
        }
        if (changed.lobbyType() != null) {
            messaging.convertAndSend("/topic/lobby",
                    RealtimeEvent.room(changed.lobbyType(), changed.roomId(), changed.lobbyPayload()));
        }
    }
}
