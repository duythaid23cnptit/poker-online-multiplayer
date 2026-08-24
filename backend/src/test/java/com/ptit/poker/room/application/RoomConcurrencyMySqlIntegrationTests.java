package com.ptit.poker.room.application;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.room.api.dto.CreateRoomRequest;
import com.ptit.poker.room.api.dto.JoinRoomRequest;
import com.ptit.poker.room.domain.RoomType;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
class RoomConcurrencyMySqlIntegrationTests {
    @Autowired RoomApplicationService service;
    @Autowired UserRepository users;
    @Autowired RoomPlayerRepository members;
    @Autowired PasswordEncoder passwords;

    @Test
    void sameSeatRaceHasOneWinnerAndConservesChips() throws Exception {
        UserEntity owner = user(1_000); UserEntity first = user(1_000); UserEntity second = user(1_000);
        Long roomId = service.create(owner.getId(), room()).room().id();

        List<Throwable> failures = runTogether(
                () -> service.join(first.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null)),
                () -> service.join(second.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null)));

        assertThat(failures).hasSize(1);
        var occupied = members.findAllByRoomIdAndLeftAtIsNullOrderById(roomId).stream()
                .filter(member -> Integer.valueOf(1).equals(member.getSeatNumber())).toList();
        assertThat(occupied).hasSize(1);
        long accountTotal = users.findById(first.getId()).orElseThrow().getAccountChips()
                + users.findById(second.getId()).orElseThrow().getAccountChips();
        long tableTotal = members.findByRoomIdAndUserId(roomId, first.getId()).map(member -> member.getTableChips()).orElse(0L)
                + members.findByRoomIdAndUserId(roomId, second.getId()).map(member -> member.getTableChips()).orElse(0L);
        assertThat(accountTotal + tableTotal).isEqualTo(2_000);
    }

    @Test
    void sameAccountDoubleJoinDebitsOnlyOneEffectiveMembership() throws Exception {
        UserEntity owner = user(1_000); UserEntity joining = user(1_000);
        Long roomId = service.create(owner.getId(), room()).room().id();

        List<Throwable> failures = runTogether(
                () -> service.join(joining.getId(), roomId, new JoinRoomRequest(false, 1, 100L, null)),
                () -> service.join(joining.getId(), roomId, new JoinRoomRequest(false, 2, 100L, null)));

        assertThat(failures).hasSize(1);
        var membership = members.findByRoomIdAndUserId(roomId, joining.getId()).orElseThrow();
        long accountChips = users.findById(joining.getId()).orElseThrow().getAccountChips();
        assertThat(accountChips).isGreaterThanOrEqualTo(0);
        assertThat(membership.getTableChips()).isEqualTo(100);
        assertThat(accountChips + membership.getTableChips()).isEqualTo(1_000);
    }

    private List<Throwable> runTogether(ThrowingAction first, ThrowingAction second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = List.of(
                    executor.submit(() -> invoke(ready, start, first)),
                    executor.submit(() -> invoke(ready, start, second)));
            ready.await(); start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable failure = future.get();
                if (failure != null) failures.add(failure);
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Throwable invoke(CountDownLatch ready, CountDownLatch start, ThrowingAction action) {
        ready.countDown();
        try { start.await(); action.run(); return null; }
        catch (Throwable failure) { return failure; }
    }

    private UserEntity user(long chips) {
        String username = "race_" + UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new UserEntity(username, passwords.encode("password"), null,
                Role.PLAYER, AccountStatus.ACTIVE, chips));
    }

    private static CreateRoomRequest room() {
        return new CreateRoomRequest("Race Room", RoomType.PUBLIC, 6, 5, 10, 100, null);
    }

    @FunctionalInterface private interface ThrowingAction { void run(); }
}
