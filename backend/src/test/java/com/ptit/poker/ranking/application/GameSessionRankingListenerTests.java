package com.ptit.poker.ranking.application;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import com.ptit.poker.game.application.GameSessionFinishedEvent;import org.junit.jupiter.api.Test;
class GameSessionRankingListenerTests {
 @Test void projectionFailureIsContainedAndLaterRetryRemainsPossible(){RankingService ranking=mock(RankingService.class);doThrow(new IllegalStateException("projection unavailable")).doNothing().when(ranking).rateCompletedSession(9);GameSessionRankingListener listener=new GameSessionRankingListener(ranking);assertThatCode(()->listener.finished(new GameSessionFinishedEvent(9))).doesNotThrowAnyException();assertThatCode(()->listener.finished(new GameSessionFinishedEvent(9))).doesNotThrowAnyException();verify(ranking,times(2)).rateCompletedSession(9);}
}
