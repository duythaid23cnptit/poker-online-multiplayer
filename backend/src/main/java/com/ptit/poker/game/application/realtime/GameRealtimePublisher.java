package com.ptit.poker.game.application.realtime;

import com.ptit.poker.game.api.realtime.GameRealtimeEvent;

public interface GameRealtimePublisher {
    void publishPublic(GameRealtimeEvent event);
    void publishPrivate(long userId, GameRealtimeEvent event);
}
