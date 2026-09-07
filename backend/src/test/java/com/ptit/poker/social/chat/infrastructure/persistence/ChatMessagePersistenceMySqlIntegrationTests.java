package com.ptit.poker.social.chat.infrastructure.persistence;

import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import com.ptit.poker.social.chat.application.ChatMessagePersistencePort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
class ChatMessagePersistenceMySqlIntegrationTests {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatMessagePersistencePort messages;

    private final List<Long> roomIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (long roomId : roomIds) jdbc.update("DELETE FROM chat_messages WHERE room_id=?", roomId);
        for (long roomId : roomIds) jdbc.update("DELETE FROM rooms WHERE id=?", roomId);
        for (long userId : userIds) jdbc.update("DELETE FROM users WHERE id=?", userId);
    }

    @Test
    void flywayV11CreatesChatMessagesAndHibernateValidatesIt() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("12");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name='chat_messages'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void persistsVietnameseAndEmojiExactly() {
        Fixture fixture = fixture();
        String content = "Chào bàn poker Việt Nam 🇻🇳 — tố nhé 🃏😀";
        long id = insert(fixture.room(), fixture.sender(), UUID.randomUUID(), content);
        assertThat(jdbc.queryForObject("SELECT content FROM chat_messages WHERE id=?", String.class, id))
                .isEqualTo(content);
    }

    @Test
    void roomForeignKeyRejectsUnknownRoom() {
        long sender = user();
        assertThatThrownBy(() -> insert(Long.MAX_VALUE, sender, UUID.randomUUID(), "hello"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void senderForeignKeyRejectsUnknownUser() {
        long owner = user(); long room = room(owner);
        assertThatThrownBy(() -> insert(room, Long.MAX_VALUE, UUID.randomUUID(), "hello"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameRoomSenderAndClientMessageIdIsUnique() {
        Fixture fixture = fixture(); UUID command = UUID.randomUUID();
        insert(fixture.room(), fixture.sender(), command, "hello");
        assertThatThrownBy(() -> insert(fixture.room(), fixture.sender(), command, "hello"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count(fixture.room(), fixture.sender(), command)).isOne();
    }

    @Test
    void sameClientMessageIdMayBeUsedInDifferentRoom() {
        long sender = user(); long first = room(sender); long second = room(sender); UUID command = UUID.randomUUID();
        insert(first, sender, command, "one"); insert(second, sender, command, "two");
        assertThat(count(first, sender, command)).isOne();
        assertThat(count(second, sender, command)).isOne();
    }

    @Test
    void sameClientMessageIdMayBeUsedByDifferentSender() {
        long owner = user(); long other = user(); long room = room(owner); UUID command = UUID.randomUUID();
        insert(room, owner, command, "one"); insert(room, other, command, "two");
        assertThat(count(room, owner, command)).isOne();
        assertThat(count(room, other, command)).isOne();
    }

    @Test
    void historyUsesDeterministicDescendingKeysetWindow() {
        Fixture fixture = fixture();
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) ids.add(insert(fixture.room(), fixture.sender(), UUID.randomUUID(), "m" + i));
        List<Long> latest = history(fixture.room(), Long.MAX_VALUE, 3);
        assertThat(latest).containsExactly(ids.get(4), ids.get(3), ids.get(2));
        assertThat(history(fixture.room(), ids.get(2), 3)).containsExactly(ids.get(1), ids.get(0));
    }

    @Test
    void historyQueryIsolatesRooms() {
        long sender = user(); long first = room(sender); long second = room(sender);
        long firstId = insert(first, sender, UUID.randomUUID(), "first");
        insert(second, sender, UUID.randomUUID(), "second");
        assertThat(history(first, Long.MAX_VALUE, 10)).containsExactly(firstId);
    }

    @Test
    void persistencePortReturnsNewestBoundedWindowInChronologicalOrder() {
        Fixture fixture = fixture();
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) ids.add(insert(fixture.room(), fixture.sender(), UUID.randomUUID(), "m" + i));

        assertThat(messages.findRecentByRoomId(fixture.room(), 3))
                .extracting(record -> record.messageId())
                .containsExactly(ids.get(2), ids.get(3), ids.get(4));
    }

    @Test
    void concurrentDuplicateInsertHasOneSuccessAndOneConstraintFailure() throws Exception {
        Fixture fixture = fixture(); UUID command = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> first = executor.submit(() -> concurrentInsert(fixture, command, ready, start));
            Future<Throwable> second = executor.submit(() -> concurrentInsert(fixture, command, ready, start));
            ready.await(); start.countDown();
            List<Throwable> results = Arrays.asList(first.get(), second.get());
            assertThat(results).filteredOn(result -> result == null).hasSize(1);
            assertThat(results).filteredOn(DataIntegrityViolationException.class::isInstance).hasSize(1);
            assertThat(count(fixture.room(), fixture.sender(), command)).isOne();
        }
    }

    private Throwable concurrentInsert(Fixture fixture, UUID command, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try { start.await(); insert(fixture.room(), fixture.sender(), command, "same"); return null; }
        catch (Throwable failure) { return failure; }
    }

    private Fixture fixture() { long sender = user(); return new Fixture(room(sender), sender); }

    private long user() {
        String suffix = UUID.randomUUID().toString();
        long id = generated("""
                INSERT INTO users (username, password_hash, email, role, account_status, account_chips)
                VALUES (?, 'test-hash', ?, 'PLAYER', 'ACTIVE', 0)
                """, List.of("chat-" + suffix, "chat-" + suffix + "@example.test"), Types.VARCHAR, Types.VARCHAR);
        userIds.add(id); return id;
    }

    private long room(long owner) {
        long id = generated("""
                INSERT INTO rooms (name, owner_user_id, room_type, max_players, small_blind, big_blind,
                                   buy_in, status, last_activity_at)
                VALUES (?, ?, 'PUBLIC', 6, 5, 10, 100, 'WAITING', UTC_TIMESTAMP(6))
                """, List.of("Chat " + UUID.randomUUID(), owner), Types.VARCHAR, Types.BIGINT);
        roomIds.add(id); return id;
    }

    private long insert(long room, long sender, UUID command, String content) {
        return generated("""
                INSERT INTO chat_messages (room_id, sender_user_id, client_message_id, content, created_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6))
                """, List.of(room, sender, command.toString(), content),
                Types.BIGINT, Types.BIGINT, Types.CHAR, Types.VARCHAR);
    }

    private long generated(String sql, List<?> parameters, int... types) {
        PreparedStatementCreatorFactory factory = new PreparedStatementCreatorFactory(sql, types);
        factory.setReturnGeneratedKeys(true);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(factory.newPreparedStatementCreator(parameters), keys);
        assertThat(keys.getKey()).isNotNull(); return keys.getKey().longValue();
    }

    private int count(long room, long sender, UUID command) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM chat_messages
                WHERE room_id=? AND sender_user_id=? AND client_message_id=?
                """, Integer.class, room, sender, command.toString());
    }

    private List<Long> history(long room, long before, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM chat_messages WHERE room_id=? AND id < ? ORDER BY id DESC LIMIT ?
                """, Long.class, room, before, limit);
    }

    private record Fixture(long room, long sender) { }
}
