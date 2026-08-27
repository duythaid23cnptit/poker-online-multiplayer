package com.ptit.poker.game.infrastructure.realtime;

import com.ptit.poker.game.application.realtime.TurnTimerScheduler;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("!bootstrap")
public class ExecutorTurnTimerScheduler implements TurnTimerScheduler {
    private static final Logger LOGGER=LoggerFactory.getLogger(ExecutorTurnTimerScheduler.class);
    private final Clock clock;
    private final ScheduledThreadPoolExecutor executor;
    public ExecutorTurnTimerScheduler(Clock clock){this.clock=clock;this.executor=new ScheduledThreadPoolExecutor(2,r->{
        Thread thread=new Thread(r,"poker-turn-timer");thread.setDaemon(true);return thread;});
        executor.setRemoveOnCancelPolicy(true);}
    public Cancellable schedule(Instant deadline,Runnable task){Instant now=clock.instant();long delay=Math.max(0,Duration.between(now,deadline).toMillis());
        Runnable traced=()->{LOGGER.info("Turn deadline task starting deadline={} now={}",deadline,clock.instant());
            try{task.run();}finally{LOGGER.info("Turn deadline task finished deadline={} now={}",deadline,clock.instant());}};
        ScheduledFuture<?> future=executor.schedule(traced,delay,TimeUnit.MILLISECONDS);
        LOGGER.info("Turn deadline task scheduled deadline={} now={} delayMs={} cancelled={} done={} executorShutdown={} executorTerminated={}",
                deadline,now,delay,future.isCancelled(),future.isDone(),executor.isShutdown(),executor.isTerminated());
        return handle(future);}
    public Cancellable scheduleAtFixedRate(Duration cadence,Runnable task){ScheduledFuture<?> future=executor.scheduleAtFixedRate(
        task,cadence.toMillis(),cadence.toMillis(),TimeUnit.MILLISECONDS);return handle(future);}
    private static Cancellable handle(ScheduledFuture<?> future){return new Cancellable(){
        public void cancel(){boolean cancelled=future.cancel(false);LOGGER.info("Turn timer task cancellation requested accepted={} cancelled={} done={}",
                cancelled,future.isCancelled(),future.isDone());}public boolean isCancelled(){return future.isCancelled();}
        public boolean isDone(){return future.isDone();}};}
    @PreDestroy public void shutdown(){executor.shutdownNow();}
}
