package com.ptit.poker.social.presence.infrastructure.persistence;

import com.ptit.poker.social.presence.application.AcceptedFriendQueryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("!bootstrap")
class JdbcAcceptedFriendQueryAdapter implements AcceptedFriendQueryPort {
    private final JdbcTemplate jdbc;

    JdbcAcceptedFriendQueryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Long> findAcceptedFriendIds(long userId) {
        return jdbc.queryForList("""
                SELECT CASE WHEN requester_user_id=? THEN recipient_user_id ELSE requester_user_id END AS friend_id
                FROM friendships
                WHERE status='ACCEPTED' AND (requester_user_id=? OR recipient_user_id=?)
                ORDER BY friend_id
                """, Long.class, userId, userId, userId);
    }
}
