package com.ptit.poker.room.application;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.room.api.dto.CreateRoomRequest;
import com.ptit.poker.room.api.dto.JoinRoomRequest;
import com.ptit.poker.room.application.exception.RoomBusinessException;
import com.ptit.poker.room.domain.RoomPlayerState;
import com.ptit.poker.room.domain.RoomStatus;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.room.infrastructure.persistence.RoomRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
@Transactional
class RoomApplicationMySqlIntegrationTests {
    @Autowired RoomApplicationService service;
    @Autowired RoomRepository rooms;
    @Autowired RoomPlayerRepository members;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;

    @Test
    void createsPublicRoomWithPrincipalOwnerAndSpectatorMembership() {
        UserEntity owner = user(1_000);
        var detail = service.create(owner.getId(), create(RoomType.PUBLIC, null));

        var stored = rooms.findById(detail.room().id()).orElseThrow();
        assertThat(stored.getOwnerUserId()).isEqualTo(owner.getId());
        assertThat(stored.getPasswordHash()).isNull();
        assertThat(stored.getStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(members.findByRoomIdAndUserId(stored.getId(), owner.getId()).orElseThrow().getSeatNumber()).isNull();
    }

    @Test
    void privatePasswordIsHashedAndRequiredToJoin() {
        UserEntity owner = user(1_000); UserEntity joining = user(1_000);
        Long roomId = service.create(owner.getId(), create(RoomType.PRIVATE, "room-password")).room().id();
        String hash = rooms.findById(roomId).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("room-password");
        assertThat(passwords.matches("room-password", hash)).isTrue();
        assertThatThrownBy(() -> service.join(joining.getId(), roomId,
                new JoinRoomRequest(false, 1, 100L, "wrong-password")))
                .isInstanceOf(RoomBusinessException.class).hasMessage("Invalid room password");
    }

    @Test
    void seatedJoinAtomicallyTransfersAccountChipsToTableChips() {
        UserEntity owner = user(1_000); UserEntity joining = user(1_000);
        Long roomId = service.create(owner.getId(), create(RoomType.PUBLIC, null)).room().id();
        service.join(joining.getId(), roomId, new JoinRoomRequest(false, 2, 100L, null));

        assertThat(users.findById(joining.getId()).orElseThrow().getAccountChips()).isEqualTo(900);
        var member = members.findByRoomIdAndUserId(roomId, joining.getId()).orElseThrow();
        assertThat(member.getTableChips()).isEqualTo(100);
        assertThat(member.getPlayerState()).isEqualTo(RoomPlayerState.NOT_READY);
    }

    @Test
    void seatRulesAndDuplicateMembershipAreControlled() {
        UserEntity owner = user(1_000); UserEntity first = user(1_000); UserEntity second = user(1_000);
        Long roomId = service.create(owner.getId(), create(RoomType.PUBLIC, null)).room().id();
        service.join(first.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        assertThatThrownBy(() -> service.join(second.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null)))
                .isInstanceOf(RoomBusinessException.class).hasMessage("Seat is occupied");
        assertThatThrownBy(() -> service.join(second.getId(), roomId, new JoinRoomRequest(false, 7, 100L, null)))
                .isInstanceOf(RoomBusinessException.class).hasMessage("Seat is outside room capacity");
        assertThatThrownBy(() -> service.join(first.getId(), roomId, new JoinRoomRequest(false, 2, 100L, null)))
                .isInstanceOf(RoomBusinessException.class).hasMessage("User already joined this room");
    }

    @Test
    void spectatorCannotReadyButSeatedPlayerCanReadyAndUnready() {
        UserEntity owner = user(1_000); UserEntity seated = user(1_000);
        Long roomId = service.create(owner.getId(), create(RoomType.PUBLIC, null)).room().id();
        assertThatThrownBy(() -> service.setReady(owner.getId(), roomId, true))
                .isInstanceOf(RoomBusinessException.class).hasMessage("Spectators cannot ready");
        service.join(seated.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        service.setReady(seated.getId(), roomId, true);
        assertThat(members.findByRoomIdAndUserId(roomId, seated.getId()).orElseThrow().getPlayerState()).isEqualTo(RoomPlayerState.READY);
        service.setReady(seated.getId(), roomId, false);
        assertThat(members.findByRoomIdAndUserId(roomId, seated.getId()).orElseThrow().getPlayerState()).isEqualTo(RoomPlayerState.NOT_READY);
    }

    @Test
    void leaveReturnsChipsReleasesSeatAndTransfersOrClosesOwnership() {
        UserEntity owner = user(1_000); UserEntity seated = user(1_000);
        Long roomId = service.create(owner.getId(), create(RoomType.PUBLIC, null)).room().id();
        service.join(seated.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null));
        service.leave(owner.getId(), roomId);
        assertThat(rooms.findById(roomId).orElseThrow().getOwnerUserId()).isEqualTo(seated.getId());
        service.leave(seated.getId(), roomId);
        assertThat(users.findById(seated.getId()).orElseThrow().getAccountChips()).isEqualTo(1_000);
        assertThat(rooms.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.CLOSED);
    }

    private UserEntity user(long chips) {
        String username = "room_" + UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new UserEntity(username, passwords.encode("password"), null,
                Role.PLAYER, AccountStatus.ACTIVE, chips));
    }

    private static CreateRoomRequest create(RoomType type, String password) {
        return new CreateRoomRequest("Test Room", type, 6, 5, 10, 100, password);
    }
}
