package com.ptit.poker.analytics.application.timebucket;

import com.ptit.poker.game.application.GameSessionFinishedEvent;import org.slf4j.*;import org.springframework.context.annotation.Profile;import org.springframework.stereotype.Component;import org.springframework.transaction.event.*;

@Component @Profile("!bootstrap")
public class GameSessionTimeBucketAnalyticsListener {
    private static final Logger LOGGER=LoggerFactory.getLogger(GameSessionTimeBucketAnalyticsListener.class);private final TimeBucketAnalyticsService analytics;
    public GameSessionTimeBucketAnalyticsListener(TimeBucketAnalyticsService analytics){this.analytics=analytics;}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) public void finished(GameSessionFinishedEvent event){try{analytics.refreshCompletedSession(event.gameSessionId());}catch(RuntimeException failure){LOGGER.error("Time-bucket analytics refresh failed after completed game session {}",event.gameSessionId(),failure);}}
}
