package com.ptit.poker.analytics.application.ranking;
import com.ptit.poker.analytics.domain.ranking.MultiplayerEloCalculator;import java.sql.Timestamp;import java.time.*;import java.util.*;
import org.springframework.context.annotation.Profile;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;
@Service @Profile("!bootstrap") public class RankingService {
 private final RankingHistoryPort history;private final JdbcTemplate jdbc;private final Clock clock;private final MultiplayerEloCalculator elo=new MultiplayerEloCalculator();
 public RankingService(RankingHistoryPort history,JdbcTemplate jdbc,Clock clock){this.history=history;this.jdbc=jdbc;this.clock=clock;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void rateCompletedSession(long sessionId){if(!history.lockCompletedSession(sessionId))return;List<RankingHistoryPort.SessionResult> results=history.completedSessionResults(sessionId);if(results.size()<2)return;
  int existing=jdbc.queryForList("SELECT user_id FROM ranking_history WHERE game_session_id=?",Long.class,sessionId).size();if(existing==results.size())return;if(existing>0)throw new IllegalStateException("partial ranking history for game session "+sessionId);
  List<Long> ids=results.stream().map(RankingHistoryPort.SessionResult::userId).distinct().sorted().toList();
  for(long id:ids)jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,id);
  Instant now=clock.instant();for(long id:ids)jdbc.update("INSERT IGNORE INTO player_rankings(user_id,rating,games_rated,peak_rating,updated_at) VALUES(?,1000,0,1000,?)",id,Timestamp.from(now));
  Map<Long,Rating> ratings=new HashMap<>();for(long id:ids){Rating rating=jdbc.queryForObject("SELECT rating,games_rated,peak_rating FROM player_rankings WHERE user_id=? FOR UPDATE",(rs,n)->new Rating(rs.getInt(1),rs.getLong(2),rs.getInt(3)),id);ratings.put(id,rating);}
  List<MultiplayerEloCalculator.Result> input=results.stream().map(r->new MultiplayerEloCalculator.Result(r.userId(),ratings.get(r.userId()).rating,r.sessionNet())).toList();
  for(var c:elo.calculate(input)){Rating old=ratings.get(c.userId());jdbc.update("UPDATE player_rankings SET rating=?,games_rated=?,peak_rating=?,updated_at=? WHERE user_id=?",c.newRating(),Math.addExact(old.games,1),Math.max(old.peak,c.newRating()),Timestamp.from(now),c.userId());
   jdbc.update("INSERT INTO ranking_history(user_id,game_session_id,old_rating,new_rating,rating_delta,session_net,placement,participant_count,created_at) VALUES(?,?,?,?,?,?,?,?,?)",c.userId(),sessionId,c.oldRating(),c.newRating(),c.ratingDelta(),c.sessionNet(),c.placement(),c.participantCount(),Timestamp.from(now));}}
 public CurrentRanking current(long userId){List<CurrentRanking> rows=jdbc.query("""
  SELECT ranked.rank_position,ranked.user_id,ranked.rating,ranked.games_rated,ranked.peak_rating FROM
  (SELECT ROW_NUMBER() OVER(ORDER BY rating DESC,games_rated DESC,user_id ASC) rank_position,user_id,rating,games_rated,peak_rating FROM player_rankings WHERE games_rated>0) ranked WHERE ranked.user_id=?
  """,(rs,n)->new CurrentRanking(rs.getLong(1),rs.getLong(2),rs.getInt(3),rs.getLong(4),rs.getInt(5)),userId);
  return rows.isEmpty()?new CurrentRanking(null,userId,1000,0,1000):rows.getFirst();}
 public Page leaderboard(int page,int size){validate(page,size);long total=Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM player_rankings WHERE games_rated>0",Long.class)).orElse(0L);List<CurrentRanking> items=jdbc.query("""
  SELECT rank_position,user_id,rating,games_rated,peak_rating FROM (SELECT ROW_NUMBER() OVER(ORDER BY rating DESC,games_rated DESC,user_id ASC) rank_position,user_id,rating,games_rated,peak_rating FROM player_rankings WHERE games_rated>0) x ORDER BY rank_position LIMIT ? OFFSET ?
  """,(rs,n)->new CurrentRanking(rs.getLong(1),rs.getLong(2),rs.getInt(3),rs.getLong(4),rs.getInt(5)),size,Math.multiplyExact(page,size));return new Page(items,page,size,total);}
 public List<History> history(long userId,int page,int size){validate(page,size);return jdbc.query("SELECT game_session_id,old_rating,new_rating,rating_delta,session_net,placement,participant_count,created_at FROM ranking_history WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(rs,n)->new History(rs.getLong(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getLong(5),rs.getInt(6),rs.getInt(7),rs.getTimestamp(8).toInstant()),userId,size,Math.multiplyExact(page,size));}
 private static void validate(int page,int size){if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"page must be non-negative and size between 1 and 100");}
 private record Rating(int rating,long games,int peak){} public record CurrentRanking(Long rank,long userId,int rating,long gamesRated,int peakRating){} public record Page(List<CurrentRanking> items,int page,int size,long total){} public record History(long gameSessionId,int oldRating,int newRating,int ratingDelta,long sessionNet,int placement,int participantCount,Instant createdAt){}
}
