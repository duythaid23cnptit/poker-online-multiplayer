package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.game.application.realtime.HandTransitionScheduler;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class ExecutorHandTransitionScheduler implements HandTransitionScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorHandTransitionScheduler.class);
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "poker-hand-transition");
        thread.setDaemon(true);
        return thread;
    });

    public ExecutorHandTransitionScheduler() { executor.setRemoveOnCancelPolicy(true); }

    @Override
    public Cancellable schedule(Duration delay, Runnable task) {
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                task.run();
            } catch (Throwable failure) {
                LOGGER.error("Scheduled hand transition failed unexpectedly", failure);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        return new Cancellable() {
            @Override public void cancel() { future.cancel(false); }
            @Override public boolean isCancelled() { return future.isCancelled(); }
            @Override public boolean isDone() { return future.isDone(); }
        };
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
