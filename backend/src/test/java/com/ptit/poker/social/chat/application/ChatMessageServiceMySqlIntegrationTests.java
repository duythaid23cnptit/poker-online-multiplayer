package com.ptit.poker.social.chat.application;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
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
import com.ptit.poker.social.application.SocialPlayerQueryPort;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.ArrayList;
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
class ChatMessageServiceMySqlIntegrationTests {
    @Autowired ChatMessageService service;
    @Autowired UserRepository users;
    @Autowired PlayerProfileRepository profiles;
    @Autowired RoomRepository rooms;
    @Autowired RoomPlayerRepository members;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> roomIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (long roomId : roomIds) jdbc.update("DELETE FROM chat_messages WHERE room_id=?", roomId);
        for (long roomId : roomIds) jdbc.update("DELETE FROM room_players WHERE room_id=?", roomId);
        for (long roomId : roomIds) jdbc.update("DELETE FROM rooms WHERE id=?", roomId);
        for (long userId : userIds) jdbc.update("DELETE FROM player_profiles WHERE user_id=?", userId);
        for (long userId : userIds) jdbc.update("DELETE FROM users WHERE id=?", userId);
    }

    @Test
    void activeSeatedMemberSendsNormalizedMessageWithSafeResponse() {
        Fixture fixture = fixture(true);
        ChatMessageView result = service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                fixture.command().toString(), "  Xin chào 👋  ");

        assertThat(result.messageId()).isPositive();
        assertThat(result.roomId()).isEqualTo(fixture.room().getId());
        assertThat(result.clientMessageId()).isEqualTo(fixture.command().toString());
        assertThat(result.content()).isEqualTo("Xin chào 👋");
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.sender()).isEqualTo(new SocialPlayerQueryPort.SafePlayerSummary(
                fixture.sender().getId(), "Alpha", null));
        assertThat(count(fixture.room(), fixture.sender(), fixture.command())).isOne();
    }

    @Test
    void currentSpectatorMaySend() {
        Fixture fixture = fixture(false);
        ChatMessageView result = service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                fixture.command().toString(), "spectator message");
        assertThat(result.messageId()).isPositive();
        assertThat(count(fixture.room(), fixture.sender(), fixture.command())).isOne();
    }

    @Test
    void nonMemberIsRejectedWithoutRow() {
        Fixture fixture = fixture(true); UserEntity outsider = user("Outsider");
        assertCode(() -> service.sendMessage(outsider.getId(), fixture.room().getId(),
                fixture.command().toString(), "hello"), "CHAT_NOT_ROOM_MEMBER");
        assertThat(count(fixture.room(), outsider, fixture.command())).isZero();
    }

    @Test
    void departedMemberIsRejectedWithoutRow() {
        Fixture fixture = fixture(true);
        RoomPlayerEntity member = members.findByRoomIdAndUserId(fixture.room().getId(), fixture.sender().getId()).orElseThrow();
        member.leave(Instant.parse("2026-08-30T01:00:00Z")); members.saveAndFlush(member);

        assertCode(() -> service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                fixture.command().toString(), "hello"), "CHAT_NOT_ROOM_MEMBER");
        assertThat(count(fixture.room(), fixture.sender(), fixture.command())).isZero();
    }

    @Test
    void closedRoomIsRejectedWithoutRow() {
        Fixture fixture = fixture(true);
        RoomEntity room = rooms.findById(fixture.room().getId()).orElseThrow();
        room.close(Instant.parse("2026-08-30T01:00:00Z")); rooms.saveAndFlush(room);

        assertCode(() -> service.sendMessage(fixture.sender().getId(), room.getId(),
                fixture.command().toString(), "hello"), "CHAT_ROOM_CLOSED");
        assertThat(count(room, fixture.sender(), fixture.command())).isZero();
    }

    @Test
    void unknownRoomIsRejectedWithoutRow() {
        UserEntity sender = user("Alpha");
        assertCode(() -> service.sendMessage(sender.getId(), Long.MAX_VALUE, UUID.randomUUID().toString(), "hello"),
                "CHAT_ROOM_NOT_FOUND");
    }

    @Test
    void invalidContentIsRejectedBeforePersistence() {
        Fixture fixture = fixture(true);
        assertCode(() -> service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                UUID.randomUUID().toString(), "  \t  "), "CHAT_INVALID_CONTENT");
        assertCode(() -> service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                UUID.randomUUID().toString(), "x".repeat(501)), "CHAT_INVALID_CONTENT");
        assertCode(() -> service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                UUID.randomUUID().toString(), "line\nnext"), "CHAT_INVALID_CONTENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE room_id=?", Integer.class,
                fixture.room().getId())).isZero();
    }

    @Test
    void newCommandPersistsExactlyOneAuthoritativeMessage() {
        Fixture fixture = fixture(true);
        ChatMessageView result = send(fixture, "first");
        assertThat(result.messageId()).isPositive();
        assertThat(count(fixture.room(), fixture.sender(), fixture.command())).isOne();
    }

    @Test
    void sameRetryReturnsSameMessageAndDoesNotDuplicate() {
        Fixture fixture = fixture(true);
        ChatMessageView first = send(fixture, "  retry me ");
        ChatMessageView retry = send(fixture, "retry me");
        assertThat(retry).isEqualTo(first);
        assertThat(count(fixture.room(), fixture.sender(), fixture.command())).isOne();
    }

    @Test
    void conflictingRetryLeavesOriginalRowUnchanged() {
        Fixture fixture = fixture(true);
        ChatMessageView first = send(fixture, "original");
        assertCode(() -> send(fixture, "different"), "CHAT_CLIENT_MESSAGE_ID_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT content FROM chat_messages WHERE id=?", String.class, first.messageId()))
                .isEqualTo("original");
        assertThat(count(fixture.room(), fixture.sender(), fixture.command())).isOne();
    }

    @Test
    void concurrentSameCommandResolvesToTheSameMessage() throws Exception {
        Fixture fixture = fixture(true);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> concurrentSend(fixture, ready, start));
            Future<Object> second = executor.submit(() -> concurrentSend(fixture, ready, start));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            Object one = first.get(); Object two = second.get();
            assertThat(one).isInstanceOf(ChatMessageView.class); assertThat(two).isInstanceOf(ChatMessageView.class);
            assertThat(((ChatMessageView) one).messageId()).isEqualTo(((ChatMessageView) two).messageId());
            assertThat(count(fixture.room(), fixture.sender(), fixture.command())).isOne();
        }
    }

    @Test
    void sameClientIdFromDifferentSendersIsAllowed() {
        Fixture first = fixture(true); UserEntity secondSender = user("Beta");
        members.saveAndFlush(new RoomPlayerEntity(first.room().getId(), secondSender.getId(), null,
                RoomPlayerState.SPECTATING, 0));
        ChatMessageView one = service.sendMessage(first.sender().getId(), first.room().getId(),
                first.command().toString(), "one");
        ChatMessageView two = service.sendMessage(secondSender.getId(), first.room().getId(),
                first.command().toString(), "two");
        assertThat(two.messageId()).isNotEqualTo(one.messageId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE room_id=? AND client_message_id=?",
                Integer.class, first.room().getId(), first.command().toString())).isEqualTo(2);
    }

    @Test
    void missingSenderProjectionRollsBackInsertedMessage() {
        Fixture fixture = fixture(true);
        jdbc.update("DELETE FROM player_profiles WHERE user_id=?", fixture.sender().getId());
        assertCode(() -> send(fixture, "will rollback"), "CHAT_SENDER_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE room_id=?", Integer.class,
                fixture.room().getId())).isZero();
    }

    @Test
    void activeSeatedMemberReadsNewestBoundedHistoryChronologically() {
        Fixture fixture = fixture(true);
        ChatMessageView first = service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                UUID.randomUUID().toString(), "first");
        ChatMessageView second = service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                UUID.randomUUID().toString(), "second");
        ChatMessageView third = service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                UUID.randomUUID().toString(), "third");

        assertThat(service.findRecentMessages(fixture.sender().getId(), fixture.room().getId(), 2))
                .extracting(ChatMessageView::messageId)
                .containsExactly(second.messageId(), third.messageId())
                .doesNotContain(first.messageId());
    }

    @Test
    void activeSpectatorReadsHistoryWithSafeSenderProjection() {
        Fixture fixture = fixture(false);
        ChatMessageView sent = service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                fixture.command().toString(), "spectator history");

        assertThat(service.findRecentMessages(fixture.sender().getId(), fixture.room().getId(), 50))
                .containsExactly(sent);
        assertThat(sent.sender()).isEqualTo(new SocialPlayerQueryPort.SafePlayerSummary(
                fixture.sender().getId(), "Alpha", null));
    }

    @Test
    void outsiderAndDepartedMemberCannotReadHistory() {
        Fixture fixture = fixture(true);
        service.sendMessage(fixture.sender().getId(), fixture.room().getId(),
                fixture.command().toString(), "private room message");
        UserEntity outsider = user("Outsider");
        assertCode(() -> service.findRecentMessages(outsider.getId(), fixture.room().getId(), 50),
                "CHAT_NOT_ROOM_MEMBER");

        RoomPlayerEntity member = members.findByRoomIdAndUserId(
                fixture.room().getId(), fixture.sender().getId()).orElseThrow();
        member.leave(Instant.parse("2026-08-30T01:00:00Z"));
        members.saveAndFlush(member);
        assertCode(() -> service.findRecentMessages(fixture.sender().getId(), fixture.room().getId(), 50),
                "CHAT_NOT_ROOM_MEMBER");
    }

    private Object concurrentSend(Fixture fixture, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try { start.await(); return send(fixture, "same"); }
        catch (Throwable failure) { return failure; }
    }

    private ChatMessageView send(Fixture fixture, String content) {
        return service.sendMessage(fixture.sender().getId(), fixture.room().getId(), fixture.command().toString(), content);
    }

    private Fixture fixture(boolean seated) {
        UserEntity sender = user("Alpha");
        RoomEntity room = rooms.saveAndFlush(new RoomEntity("chat-" + UUID.randomUUID(), sender.getId(),
                RoomType.PUBLIC, null, 6, 5, 10, 100, RoomStatus.WAITING,
                Instant.parse("2026-08-30T01:00:00Z")));
        roomIds.add(room.getId());
        members.saveAndFlush(new RoomPlayerEntity(room.getId(), sender.getId(), seated ? 1 : null,
                seated ? RoomPlayerState.NOT_READY : RoomPlayerState.SPECTATING, seated ? 100 : 0));
        return new Fixture(room, sender, UUID.randomUUID());
    }

    private UserEntity user(String displayName) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = users.saveAndFlush(new UserEntity("chat-svc-" + suffix, "test-hash",
                "chat-svc-" + suffix + "@example.test", Role.PLAYER, AccountStatus.ACTIVE, 0));
        userIds.add(user.getId());
        profiles.saveAndFlush(new PlayerProfileEntity(user.getId(), displayName, null, PresenceStatus.OFFLINE));
        return user;
    }

    private int count(RoomEntity room, UserEntity sender, UUID command) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM chat_messages
                WHERE room_id=? AND sender_user_id=? AND client_message_id=?
                """, Integer.class, room.getId(), sender.getId(), command.toString());
    }

    private static void assertCode(ThrowingCall call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ChatMessageException.class)
                .extracting(failure -> ((ChatMessageException) failure).code()).isEqualTo(code);
    }

    private record Fixture(RoomEntity room, UserEntity sender, UUID command) { }
    @FunctionalInterface private interface ThrowingCall { void run(); }
}
