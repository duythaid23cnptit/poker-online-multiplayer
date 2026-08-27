package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.game.application.realtime.ReconnectGraceScheduler;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!bootstrap")
public class ExecutorReconnectGraceScheduler implements ReconnectGraceScheduler {
    private final Clock clock;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2, runnable -> {
        Thread thread = new Thread(runnable, "poker-reconnect-grace"); thread.setDaemon(true); return thread;
    });
    public ExecutorReconnectGraceScheduler(Clock clock) { this.clock = clock; executor.setRemoveOnCancelPolicy(true); }
    @Override public Cancellable schedule(Instant deadline, Runnable task) {
        var future = executor.schedule(task, Math.max(0, deadline.toEpochMilli() - clock.instant().toEpochMilli()), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }
    @PreDestroy void shutdown() { executor.shutdownNow(); }
}
