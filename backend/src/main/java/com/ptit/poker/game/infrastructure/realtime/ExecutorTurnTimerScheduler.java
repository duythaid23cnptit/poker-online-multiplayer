package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.game.application.realtime.TurnTimerScheduler;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.concurrent.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("!bootstrap")
public class ExecutorTurnTimerScheduler implements TurnTimerScheduler {
    private final Clock clock;
    private final ScheduledThreadPoolExecutor executor;
    public ExecutorTurnTimerScheduler(Clock clock){this.clock=clock;this.executor=new ScheduledThreadPoolExecutor(2,r->{
        Thread thread=new Thread(r,"poker-turn-timer");thread.setDaemon(true);return thread;});
        executor.setRemoveOnCancelPolicy(true);}
    public Cancellable schedule(Instant deadline,Runnable task){long delay=Math.max(0,Duration.between(clock.instant(),deadline).toMillis());
        ScheduledFuture<?> future=executor.schedule(task,delay,TimeUnit.MILLISECONDS);return ()->future.cancel(false);}
    public Cancellable scheduleAtFixedRate(Duration cadence,Runnable task){ScheduledFuture<?> future=executor.scheduleAtFixedRate(
        task,cadence.toMillis(),cadence.toMillis(),TimeUnit.MILLISECONDS);return ()->future.cancel(false);}
    @PreDestroy public void shutdown(){executor.shutdownNow();}
}
