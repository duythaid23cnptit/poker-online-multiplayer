package com.ptit.poker.social.infrastructure.persistence;

import com.ptit.poker.social.application.FriendshipPersistencePort;
import com.ptit.poker.social.application.FriendshipRecord;
import com.ptit.poker.social.domain.FriendshipStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!bootstrap")
class JdbcFriendshipPersistenceAdapter implements FriendshipPersistencePort {
    private static final String COLUMNS = """
            id, requester_user_id, recipient_user_id, status, created_at, responded_at, version
            """;
    private static final RowMapper<FriendshipRecord> ROW_MAPPER = JdbcFriendshipPersistenceAdapter::map;
    private final JdbcTemplate jdbc;

    JdbcFriendshipPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertPending(long requesterUserId, long recipientUserId, Instant createdAt) {
        return jdbc.update("""
                INSERT IGNORE INTO friendships
                    (requester_user_id, recipient_user_id, status, created_at, responded_at, version)
                VALUES (?, ?, 'PENDING', ?, NULL, 0)
                """, requesterUserId, recipientUserId, createdAt) == 1;
    }

    @Override
    public Optional<FriendshipRecord> findCanonicalPairForUpdate(long userA, long userB) {
        List<FriendshipRecord> rows = jdbc.query("SELECT " + COLUMNS + """
                FROM friendships
                WHERE lower_user_id = LEAST(?, ?) AND higher_user_id = GREATEST(?, ?)
                FOR UPDATE
                """, ROW_MAPPER, userA, userB, userA, userB);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<FriendshipRecord> findRequestByIdForUpdate(long requestId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM friendships WHERE id=? FOR UPDATE",
                ROW_MAPPER, requestId).stream().findFirst();
    }

    @Override
    public FriendshipRecord transition(long requestId, FriendshipStatus status, Instant respondedAt) {
        int changed = jdbc.update("""
                UPDATE friendships SET status=?, responded_at=?, version=version+1 WHERE id=?
                """, status.name(), respondedAt, requestId);
        if (changed != 1) throw new IllegalStateException("Friendship transition did not update exactly one row");
        return findById(requestId);
    }

    @Override
    public FriendshipRecord reopen(long requestId, long requesterUserId, long recipientUserId, Instant createdAt) {
        int changed = jdbc.update("""
                UPDATE friendships
                SET requester_user_id=?, recipient_user_id=?, status='PENDING', created_at=?,
                    responded_at=NULL, version=version+1
                WHERE id=?
                """, requesterUserId, recipientUserId, createdAt, requestId);
        if (changed != 1) throw new IllegalStateException("Friendship reopen did not update exactly one row");
        return findById(requestId);
    }

    @Override
    public boolean deleteAcceptedPair(long userA, long userB) {
        return jdbc.update("""
                DELETE FROM friendships
                WHERE lower_user_id=LEAST(?, ?) AND higher_user_id=GREATEST(?, ?) AND status='ACCEPTED'
                """, userA, userB, userA, userB) == 1;
    }

    @Override
    public List<FriendshipRecord> listIncomingPending(long recipientUserId) {
        return jdbc.query("SELECT " + COLUMNS + """
                FROM friendships WHERE recipient_user_id=? AND status='PENDING'
                ORDER BY created_at DESC, id DESC
                """, ROW_MAPPER, recipientUserId);
    }

    @Override
    public List<FriendshipRecord> listOutgoingPending(long requesterUserId) {
        return jdbc.query("SELECT " + COLUMNS + """
                FROM friendships WHERE requester_user_id=? AND status='PENDING'
                ORDER BY created_at DESC, id DESC
                """, ROW_MAPPER, requesterUserId);
    }

    @Override
    public List<FriendshipRecord> listAccepted(long userId) {
        return jdbc.query("SELECT " + COLUMNS + """
                FROM friendships
                WHERE status='ACCEPTED' AND (requester_user_id=? OR recipient_user_id=?)
                """, ROW_MAPPER, userId, userId);
    }

    private FriendshipRecord findById(long requestId) {
        return jdbc.queryForObject("SELECT " + COLUMNS + " FROM friendships WHERE id=?", ROW_MAPPER, requestId);
    }

    private static FriendshipRecord map(ResultSet result, int rowNumber) throws SQLException {
        var respondedAt = result.getTimestamp("responded_at");
        return new FriendshipRecord(
                result.getLong("id"), result.getLong("requester_user_id"), result.getLong("recipient_user_id"),
                FriendshipStatus.valueOf(result.getString("status")), result.getTimestamp("created_at").toInstant(),
                respondedAt == null ? null : respondedAt.toInstant(), result.getLong("version"));
    }
}
