package com.ptit.poker.analytics.infrastructure.history;
import com.ptit.poker.analytics.application.ranking.RankingHistoryPort;import java.util.*;import org.springframework.context.annotation.Profile;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;
@Component @Profile("!bootstrap") public class JdbcRankingHistoryAdapter implements RankingHistoryPort {private final JdbcTemplate jdbc;public JdbcRankingHistoryAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean lockCompletedSession(long id){List<String> status=jdbc.query("SELECT status FROM game_sessions WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1),id);return status.size()==1&&"FINISHED".equals(status.getFirst());}
 public List<SessionResult> completedSessionResults(long id){return jdbc.query("""
  SELECT hp.user_id,SUM(hp.ending_table_chips-hp.starting_table_chips) FROM hand_players hp
  JOIN poker_hands ph ON ph.id=hp.poker_hand_id JOIN game_sessions gs ON gs.id=ph.game_session_id
  WHERE gs.id=? AND gs.status='FINISHED' AND ph.ended_at IS NOT NULL GROUP BY hp.user_id ORDER BY hp.user_id
  """,(rs,n)->new SessionResult(rs.getLong(1),rs.getLong(2)),id);}}
