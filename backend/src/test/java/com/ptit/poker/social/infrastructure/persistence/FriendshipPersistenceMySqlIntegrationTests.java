package com.ptit.poker.social.infrastructure.persistence;

import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
class FriendshipPersistenceMySqlIntegrationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayV10CreatesFriendshipsAndHibernateValidatesIt() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("12");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='friendships'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void selfPairConstraintRejectsRequest() {
        long user = insertUser();
        try {
            Throwable failure = catchThrowable(() -> insertFriendship(user, user, "PENDING"));
            assertCheckConstraintViolation(failure);
            assertThat(countPair(user, user)).isZero();
        } finally {
            deleteUsers(user);
        }
    }

    @Test
    void canonicalUniqueConstraintRejectsSameDirectionDuplicate() {
        long[] users = insertPair();
        try {
            insertFriendship(users[0], users[1], "PENDING");
            assertThatThrownBy(() -> insertFriendship(users[0], users[1], "PENDING"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(countPair(users[0], users[1])).isEqualTo(1);
        } finally {
            deletePair(users);
        }
    }

    @Test
    void canonicalUniqueConstraintRejectsOppositeDirectionDuplicate() {
        long[] users = insertPair();
        try {
            insertFriendship(users[0], users[1], "PENDING");
            assertThatThrownBy(() -> insertFriendship(users[1], users[0], "PENDING"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(countPair(users[0], users[1])).isEqualTo(1);
        } finally {
            deletePair(users);
        }
    }

    @Test
    void generatedColumnsCanonicalizeReverseDirection() {
        long[] users = insertPair();
        long lower = Math.min(users[0], users[1]);
        long higher = Math.max(users[0], users[1]);
        try {
            insertFriendship(higher, lower, "PENDING");
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT lower_user_id, higher_user_id FROM friendships WHERE lower_user_id=? AND higher_user_id=?",
                    lower, higher);
            assertThat(((Number) row.get("lower_user_id")).longValue()).isEqualTo(lower);
            assertThat(((Number) row.get("higher_user_id")).longValue()).isEqualTo(higher);
        } finally {
            deletePair(users);
        }
    }

    @Test
    void statusConstraintRejectsUnknownValue() {
        long[] users = insertPair();
        try {
            Throwable failure = catchThrowable(() -> insertFriendship(users[0], users[1], "UNKNOWN"));
            SQLException sqlFailure = assertCheckConstraintViolation(failure);
            assertThat(sqlFailure.getMessage()).containsIgnoringCase("chk_friendships_status");
            assertThat(countPair(users[0], users[1])).isZero();
        } finally {
            deletePair(users);
        }
    }

    @Test
    void foreignKeysRejectUnknownUsersAndCanonicalUniqueIndexExists() {
        assertThatThrownBy(() -> insertFriendship(Long.MAX_VALUE - 1, Long.MAX_VALUE, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);

        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND table_name='friendships'
                  AND index_name='uk_friendships_canonical_pair' AND non_unique=0
                ORDER BY seq_in_index
                """, String.class);
        assertThat(columns).containsExactly("lower_user_id", "higher_user_id");
    }

    @Test
    void concurrentOppositeDirectionInsertsProduceExactlyOneRow() throws Exception {
        long[] users = insertPair();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> first = executor.submit(() -> concurrentInsert(users[0], users[1], ready, start));
            Future<Throwable> second = executor.submit(() -> concurrentInsert(users[1], users[0], ready, start));
            ready.await();
            start.countDown();

            List<Throwable> results = Arrays.asList(first.get(), second.get());
            assertThat(results).filteredOn(result -> result == null).hasSize(1);
            assertThat(results).filteredOn(DataIntegrityViolationException.class::isInstance).hasSize(1);
            assertThat(countPair(users[0], users[1])).isEqualTo(1);
        } finally {
            deletePair(users);
        }
    }

    private Throwable concurrentInsert(long requester, long recipient, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            insertFriendship(requester, recipient, "PENDING");
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static SQLException assertCheckConstraintViolation(Throwable failure) {
        assertThat(failure).isInstanceOf(DataAccessException.class);
        SQLException sqlFailure = findSqlException(failure);
        if (sqlFailure == null) {
            throw new AssertionError("Expected a nested SQLException for MySQL CHECK constraint violation", failure);
        }
        assertThat(sqlFailure.getErrorCode()).isEqualTo(3819);
        assertThat(sqlFailure.getSQLState()).isEqualTo("HY000");
        return sqlFailure;
    }

    private static SQLException findSqlException(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != current) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return current instanceof SQLException sqlException ? sqlException : null;
    }

    private long[] insertPair() {
        return new long[]{insertUser(), insertUser()};
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString();
        var statement = new PreparedStatementCreatorFactory("""
                INSERT INTO users (username, password_hash, email, role, account_status, account_chips)
                VALUES (?, 'test-hash', ?, 'PLAYER', 'ACTIVE', 0)
                """, Types.VARCHAR, Types.VARCHAR);
        statement.setReturnGeneratedKeys(true);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(statement.newPreparedStatementCreator(
                List.of("friend-" + suffix, "friend-" + suffix + "@example.test")), keys);
        assertThat(keys.getKey()).isNotNull();
        return keys.getKey().longValue();
    }

    private void insertFriendship(long requester, long recipient, String status) {
        jdbcTemplate.update("""
                INSERT INTO friendships (requester_user_id, recipient_user_id, status, created_at)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6))
                """, requester, recipient, status);
    }

    private int countPair(long userA, long userB) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM friendships WHERE lower_user_id=? AND higher_user_id=?",
                Integer.class, Math.min(userA, userB), Math.max(userA, userB));
    }

    private void deletePair(long[] users) {
        jdbcTemplate.update("DELETE FROM friendships WHERE lower_user_id=? AND higher_user_id=?",
                Math.min(users[0], users[1]), Math.max(users[0], users[1]));
        deleteUsers(users);
    }

    private void deleteUsers(long... users) {
        for (long user : users) {
            jdbcTemplate.update("DELETE FROM users WHERE id=?", user);
        }
    }
}
