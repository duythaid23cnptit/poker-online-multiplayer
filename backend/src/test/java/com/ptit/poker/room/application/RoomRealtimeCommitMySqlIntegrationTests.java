package com.ptit.poker.room.application;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.room.api.dto.CreateRoomRequest;
import com.ptit.poker.room.application.exception.RoomBusinessException;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
class RoomRealtimeCommitMySqlIntegrationTests {
    @Autowired RoomApplicationService rooms;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoBean SimpMessagingTemplate messaging;

    @Test
    void successfulMutationPublishesOnlyAfterServiceTransactionCommits() {
        UserEntity owner = user();
        rooms.create(owner.getId(), validRoom());
        verify(messaging, timeout(2_000)).convertAndSend(eq("/topic/lobby"), any(Object.class));
    }

    @Test
    void rejectedMutationPublishesNoSuccessfulRoomEvent() {
        UserEntity owner = user();
        clearInvocations(messaging);
        CreateRoomRequest invalid = new CreateRoomRequest("Invalid", RoomType.PUBLIC, 6, 10, 5, 100, null);
        assertThatThrownBy(() -> rooms.create(owner.getId(), invalid)).isInstanceOf(RoomBusinessException.class);
        verifyNoInteractions(messaging);
    }

    private UserEntity user() {
        String username = "commit_" + UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new UserEntity(username, passwords.encode("password"), null,
                Role.PLAYER, AccountStatus.ACTIVE, 1_000));
    }

    private static CreateRoomRequest validRoom() {
        return new CreateRoomRequest("Commit Room", RoomType.PUBLIC, 6, 5, 10, 100, null);
    }
}
