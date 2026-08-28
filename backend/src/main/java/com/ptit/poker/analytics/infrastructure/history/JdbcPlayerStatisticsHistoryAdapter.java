package com.ptit.poker.analytics.infrastructure.history;

import com.ptit.poker.analytics.application.PlayerStatisticsHistoryPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component @Profile("!bootstrap")
public class JdbcPlayerStatisticsHistoryAdapter implements PlayerStatisticsHistoryPort {
    private final JdbcTemplate jdbc;public JdbcPlayerStatisticsHistoryAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Long> participantUserIds(long sessionId){return jdbc.queryForList("""
        SELECT DISTINCT hp.user_id FROM hand_players hp JOIN poker_hands ph ON ph.id=hp.poker_hand_id
        JOIN game_sessions gs ON gs.id=ph.game_session_id
        WHERE gs.id=? AND gs.status='FINISHED' AND ph.ended_at IS NOT NULL
        """,Long.class,sessionId);}
    public List<CompletedHandFact> completedHands(long userId){return jdbc.query("""
        SELECT ph.game_session_id,ph.id,ph.started_at,ph.ended_at,hp.starting_table_chips,hp.ending_table_chips
        FROM hand_players hp JOIN poker_hands ph ON ph.id=hp.poker_hand_id
        JOIN game_sessions gs ON gs.id=ph.game_session_id
        WHERE hp.user_id=? AND gs.status='FINISHED' AND ph.ended_at IS NOT NULL ORDER BY ph.game_session_id,ph.hand_number
        """,(rs,row)->new CompletedHandFact(rs.getLong(1),rs.getLong(2),rs.getTimestamp(3).toInstant(),
                rs.getTimestamp(4).toInstant(),rs.getLong(5),rs.getLong(6)),userId);}
    public long largestPotAward(long userId){Long value=jdbc.queryForObject("""
        SELECT COALESCE(MAX(pa.amount_awarded),0) FROM pot_awards pa JOIN pots p ON p.id=pa.pot_id
        JOIN poker_hands ph ON ph.id=p.poker_hand_id JOIN game_sessions gs ON gs.id=ph.game_session_id
        WHERE pa.user_id=? AND gs.status='FINISHED' AND ph.ended_at IS NOT NULL
        """,Long.class,userId);return value==null?0:value;}
}
