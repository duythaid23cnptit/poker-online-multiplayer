package com.ptit.poker.social.chat.infrastructure.persistence;

import com.ptit.poker.social.chat.application.ChatMessagePersistencePort;
import com.ptit.poker.social.chat.application.ChatMessageRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.sql.Types;

@Repository
@Profile("!bootstrap")
class JdbcChatMessagePersistenceAdapter implements ChatMessagePersistencePort {
    private static final RowMapper<ChatMessageRecord> ROW_MAPPER = JdbcChatMessagePersistenceAdapter::map;
    private final JdbcTemplate jdbc;

    JdbcChatMessagePersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ChatMessageRecord insert(long roomId, long senderUserId, String clientMessageId,
                                    String content, Instant createdAt) {
        PreparedStatementCreatorFactory factory = new PreparedStatementCreatorFactory("""
                INSERT INTO chat_messages (room_id, sender_user_id, client_message_id, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, Types.BIGINT, Types.BIGINT, Types.CHAR, Types.VARCHAR, Types.TIMESTAMP);
        factory.setReturnGeneratedKeys(true);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(factory.newPreparedStatementCreator(List.of(roomId, senderUserId, clientMessageId, content, createdAt)), keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Inserted chat message did not return an ID");
        long id = key.longValue();
        return findById(id).orElseThrow(() -> new IllegalStateException("Inserted chat message disappeared"));
    }

    @Override
    public Optional<ChatMessageRecord> findByCommandKeyForUpdate(long roomId, long senderUserId, String clientMessageId) {
        List<ChatMessageRecord> rows = jdbc.query("""
                SELECT id, room_id, sender_user_id, client_message_id, content, created_at
                FROM chat_messages
                WHERE room_id=? AND sender_user_id=? AND client_message_id=?
                FOR UPDATE
                """, ROW_MAPPER, roomId, senderUserId, clientMessageId);
        return rows.stream().findFirst();
    }

    @Override
    public List<ChatMessageRecord> findRecentByRoomId(long roomId, int limit) {
        List<ChatMessageRecord> rows = jdbc.query("""
                SELECT id, room_id, sender_user_id, client_message_id, content, created_at
                FROM chat_messages
                WHERE room_id=?
                ORDER BY id DESC
                LIMIT ?
                """, ROW_MAPPER, roomId, limit);
        Collections.reverse(rows);
        return List.copyOf(rows);
    }

    private Optional<ChatMessageRecord> findById(long id) {
        return jdbc.query("""
                SELECT id, room_id, sender_user_id, client_message_id, content, created_at
                FROM chat_messages WHERE id=?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    private static ChatMessageRecord map(ResultSet result, int rowNumber) throws SQLException {
        return new ChatMessageRecord(result.getLong("id"), result.getLong("room_id"),
                result.getLong("sender_user_id"), result.getString("client_message_id"),
                result.getString("content"), result.getTimestamp("created_at").toInstant());
    }
}
