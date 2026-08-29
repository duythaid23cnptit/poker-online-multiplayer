package com.ptit.poker.analytics.infrastructure.history;

import com.ptit.poker.analytics.application.timebucket.TimeBucketAnalyticsHistoryPort;
import java.time.*;import java.util.List;
import org.springframework.context.annotation.Profile;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;

@Component @Profile("!bootstrap")
public class JdbcTimeBucketAnalyticsHistoryAdapter implements TimeBucketAnalyticsHistoryPort {
    private final JdbcTemplate jdbc;public JdbcTimeBucketAnalyticsHistoryAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<CompletedHand> completedHands(long userId,Instant from,Instant to){return jdbc.query("""
        SELECT ph.game_session_id,ph.started_at,ph.ended_at,hp.starting_table_chips,hp.ending_table_chips
        FROM hand_players hp JOIN poker_hands ph ON ph.id=hp.poker_hand_id JOIN game_sessions gs ON gs.id=ph.game_session_id
        WHERE hp.user_id=? AND gs.status='FINISHED' AND ph.ended_at>=? AND ph.ended_at<? ORDER BY ph.game_session_id,ph.hand_number
        """,(rs,n)->new CompletedHand(rs.getLong(1),instant(rs.getObject(2,LocalDateTime.class)),instant(rs.getObject(3,LocalDateTime.class)),rs.getLong(4),rs.getLong(5)),userId,utc(from),utc(to));}
    public long largestPotAward(long userId,Instant from,Instant to){Long value=jdbc.queryForObject("""
        SELECT COALESCE(MAX(pa.amount_awarded),0) FROM pot_awards pa JOIN pots p ON p.id=pa.pot_id
        JOIN poker_hands ph ON ph.id=p.poker_hand_id JOIN game_sessions gs ON gs.id=ph.game_session_id
        WHERE pa.user_id=? AND gs.status='FINISHED' AND ph.ended_at>=? AND ph.ended_at<?
        """,Long.class,userId,utc(from),utc(to));return value==null?0:value;}
    public List<SessionParticipantHand> completedSessionHands(long sessionId){return jdbc.query("""
        SELECT hp.user_id,ph.ended_at FROM hand_players hp JOIN poker_hands ph ON ph.id=hp.poker_hand_id
        JOIN game_sessions gs ON gs.id=ph.game_session_id WHERE gs.id=? AND gs.status='FINISHED' AND ph.ended_at IS NOT NULL
        """,(rs,n)->new SessionParticipantHand(rs.getLong(1),instant(rs.getObject(2,LocalDateTime.class))),sessionId);}
    private static Instant instant(LocalDateTime value){return value.toInstant(ZoneOffset.UTC);}
    private static LocalDateTime utc(Instant value){return LocalDateTime.ofInstant(value,ZoneOffset.UTC);}
}
