package com.ptit.poker.analytics.application.timebucket;

import static org.assertj.core.api.Assertions.assertThatCode;import static org.mockito.Mockito.*;import com.ptit.poker.game.application.GameSessionFinishedEvent;import org.junit.jupiter.api.Test;

class GameSessionTimeBucketAnalyticsListenerTests {
    @Test void failureIsContainedSoOtherAfterCommitConsumersRemainIndependent(){TimeBucketAnalyticsService service=mock(TimeBucketAnalyticsService.class);doThrow(new IllegalStateException("projection unavailable")).when(service).refreshCompletedSession(7);var listener=new GameSessionTimeBucketAnalyticsListener(service);assertThatCode(()->listener.finished(new GameSessionFinishedEvent(7))).doesNotThrowAnyException();verify(service).refreshCompletedSession(7);}
}
