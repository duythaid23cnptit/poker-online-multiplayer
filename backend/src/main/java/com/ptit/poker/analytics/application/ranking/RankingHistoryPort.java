package com.ptit.poker.analytics.application.ranking;
import java.util.List;
public interface RankingHistoryPort {boolean lockCompletedSession(long sessionId);List<SessionResult> completedSessionResults(long sessionId);
 record SessionResult(long userId,long sessionNet){} }
