package com.ptit.poker.game.application.realtime;

import com.ptit.poker.game.application.runtime.GameRuntimeView;

public interface RoomGameDiscoveryPublisher {
    void publishStarted(GameRuntimeView view);
}
