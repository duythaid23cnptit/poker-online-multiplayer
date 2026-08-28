package com.ptit.poker.analytics.application;

import com.ptit.poker.game.application.GameSessionFinishedEvent;
import org.slf4j.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component @Profile("!bootstrap")
public class GameSessionStatisticsListener {
    private static final Logger LOGGER=LoggerFactory.getLogger(GameSessionStatisticsListener.class);
    private final PlayerStatisticsRefreshPort refresh;public GameSessionStatisticsListener(PlayerStatisticsRefreshPort refresh){this.refresh=refresh;}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void onFinished(GameSessionFinishedEvent event){try{refresh.refreshCompletedSession(event.gameSessionId());}
        catch(RuntimeException failure){LOGGER.error("Player statistics refresh failed after completed game session {}",event.gameSessionId(),failure);}}
}
