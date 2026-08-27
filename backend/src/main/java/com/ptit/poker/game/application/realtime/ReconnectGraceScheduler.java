package com.ptit.poker.game.application.realtime;

import java.time.Instant;

public interface ReconnectGraceScheduler {
    Cancellable schedule(Instant deadline, Runnable task);
    interface Cancellable { void cancel(); }
}
