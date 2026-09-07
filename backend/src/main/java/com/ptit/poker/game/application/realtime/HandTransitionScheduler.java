package com.ptit.poker.game.application.realtime;

import java.time.Duration;

public interface HandTransitionScheduler {
    Cancellable schedule(Duration delay, Runnable task);

    interface Cancellable {
        void cancel();
        default boolean isCancelled() { return false; }
        default boolean isDone() { return false; }
    }
}
