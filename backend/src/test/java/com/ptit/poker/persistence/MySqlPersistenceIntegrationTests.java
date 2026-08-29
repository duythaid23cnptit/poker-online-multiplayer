package com.ptit.poker.persistence;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.RefreshTokenEntity;
import com.ptit.poker.auth.infrastructure.persistence.RefreshTokenRepository;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileEntity;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
import com.ptit.poker.room.domain.RoomPlayerState;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
@Transactional
class MySqlPersistenceIntegrationTests {

        private static final Set<String> REQUIRED_TABLES = Set.of(
                        "users", "player_profiles", "refresh_tokens", "rooms", "room_players",
                        "game_sessions", "poker_hands", "hand_players", "player_actions", "pots",
                        "pot_awards", "uncalled_bet_returns", "player_statistics", "player_rankings", "ranking_history",
                        "daily_statistics", "weekly_statistics", "admin_audit_log", "friendships");

        @Autowired
        private Flyway flyway;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PlayerProfileRepository playerProfileRepository;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @Autowired
        private RoomRepository roomRepository;

        @Autowired
        private RoomPlayerRepository roomPlayerRepository;

        @Test
        void flywayAndHibernateValidateTheRequiredSchema() {
                assertThat(flyway.info().current()).isNotNull();
                assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");

                Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
                                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                                String.class));
                assertThat(tables).containsAll(REQUIRED_TABLES);
        }

        @Test
        void basicJpaPersistenceWorksAcrossPhaseTwoTables() {
                UserEntity user = userRepository.saveAndFlush(newUser(1_000));
                PlayerProfileEntity profile = playerProfileRepository.saveAndFlush(
                                new PlayerProfileEntity(user.getId(), unique("display"), null, PresenceStatus.OFFLINE));
                RefreshTokenEntity token = refreshTokenRepository.saveAndFlush(new RefreshTokenEntity(
                                user.getId(), unique("token-hash"), Instant.now().plus(1, ChronoUnit.DAYS)));
                RoomEntity room = roomRepository.saveAndFlush(newRoom(user.getId(), 6));
                RoomPlayerEntity roomPlayer = roomPlayerRepository.saveAndFlush(new RoomPlayerEntity(
                                room.getId(), user.getId(), 1, RoomPlayerState.NOT_READY, 500));

                assertThat(profile.getId()).isNotNull();
                assertThat(token.getId()).isNotNull();
                assertThat(roomPlayer.getId()).isNotNull();
                assertThat(roomPlayerRepository.findByRoomIdAndUserId(room.getId(), user.getId()))
                                .hasValueSatisfying(saved -> assertThat(saved.getTableChips()).isEqualTo(500));
        }

        @Test
        void duplicateUsernameIsRejected() {
                String username = unique("user");
                userRepository.saveAndFlush(new UserEntity(
                                username, "test-hash", null, Role.PLAYER, AccountStatus.ACTIVE, 0));

                assertThatThrownBy(() -> userRepository.saveAndFlush(new UserEntity(
                                username, "another-test-hash", null, Role.PLAYER, AccountStatus.ACTIVE, 0)))
                                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void negativeAccountChipsAreRejected() {
                assertCheckConstraintViolation(
                                () -> userRepository.saveAndFlush(newUser(-1)),
                                "chk_users_account_chips_non_negative");
        }

        @Test
        void roomCapacityOutsideSixToNineIsRejected() {
                UserEntity owner = userRepository.saveAndFlush(newUser(0));

                assertCheckConstraintViolation(
                                () -> roomRepository.saveAndFlush(newRoom(owner.getId(), 5)),
                                "chk_rooms_max_players");
        }

        @Test
        void duplicateProfileForOneUserIsRejected() {
                UserEntity user = userRepository.saveAndFlush(newUser(0));
                playerProfileRepository.saveAndFlush(new PlayerProfileEntity(
                                user.getId(), unique("display"), null, PresenceStatus.OFFLINE));

                assertThatThrownBy(() -> playerProfileRepository.saveAndFlush(new PlayerProfileEntity(
                                user.getId(), unique("display"), null, PresenceStatus.OFFLINE)))
                                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void invalidRoomOwnerForeignKeyIsRejected() {
                assertThatThrownBy(() -> jdbcTemplate.update(
                                """
                                                INSERT INTO rooms
                                                    (name, owner_user_id, room_type, max_players, small_blind, big_blind,
                                                     buy_in, status, last_activity_at)
                                                VALUES (?, ?, 'PUBLIC', 6, 10, 20, 1000, 'WAITING', UTC_TIMESTAMP(6))
                                                """,
                                unique("room"), Long.MAX_VALUE))
                                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void negativeTableChipsAreRejected() {
                UserEntity user = userRepository.saveAndFlush(newUser(0));
                RoomEntity room = roomRepository.saveAndFlush(newRoom(user.getId(), 6));

                assertCheckConstraintViolation(
                                () -> roomPlayerRepository.saveAndFlush(new RoomPlayerEntity(
                                                room.getId(), user.getId(), 1, RoomPlayerState.NOT_READY, -1)),
                                "chk_room_players_table_chips_non_negative");
        }

        private static void assertCheckConstraintViolation(
                        org.assertj.core.api.ThrowableAssert.ThrowingCallable persistenceOperation,
                        String expectedConstraint) {
                Throwable failure = catchThrowable(persistenceOperation);
                assertThat(failure)
                                .as("persistence must reject CHECK constraint %s", expectedConstraint)
                                .isInstanceOfAny(DataIntegrityViolationException.class, JpaSystemException.class);
                assertThat(causeChain(failure))
                                .as("exception cause chain must identify CHECK constraint %s", expectedConstraint)
                                .containsIgnoringCase(expectedConstraint);
        }

        private static String causeChain(Throwable failure) {
                StringBuilder messages = new StringBuilder();
                Throwable current = failure;
                while (current != null) {
                        if (current.getMessage() != null) {
                                messages.append(current.getClass().getName())
                                                .append(": ").append(current.getMessage()).append('\n');
                        }
                        if (current.getCause() == current) {
                                break;
                        }
                        current = current.getCause();
                }
                return messages.toString();
        }

        private static UserEntity newUser(long accountChips) {
                return new UserEntity(
                                unique("user"), "test-hash", unique("email") + "@example.test",
                                Role.PLAYER, AccountStatus.ACTIVE, accountChips);
        }

        private static RoomEntity newRoom(Long ownerUserId, int maxPlayers) {
                return new RoomEntity(
                                unique("room"), ownerUserId, RoomType.PUBLIC, null, maxPlayers,
                                10, 20, 1_000, RoomStatus.WAITING, Instant.now());
        }

        private static String unique(String prefix) {
                return prefix + "-" + UUID.randomUUID();
        }
}
