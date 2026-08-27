package com.ptit.poker.game.application.realtime;

import java.time.Duration;
import java.time.Instant;

public interface TurnTimerScheduler {
    Cancellable schedule(Instant deadline, Runnable task);
    Cancellable scheduleAtFixedRate(Duration cadence, Runnable task);
    interface Cancellable {
        void cancel();
        default boolean isCancelled(){return false;}
        default boolean isDone(){return false;}
    }
}
