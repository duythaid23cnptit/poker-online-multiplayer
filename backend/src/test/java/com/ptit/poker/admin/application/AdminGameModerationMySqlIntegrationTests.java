package com.ptit.poker.admin.application;

import static org.assertj.core.api.Assertions.*;
import com.ptit.poker.auth.domain.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.infrastructure.persistence.*;
import com.ptit.poker.room.domain.*;
import com.ptit.poker.room.infrastructure.persistence.*;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_USERNAME",matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_PASSWORD",matches=".+")
@ActiveProfiles("test") @ContextConfiguration(initializers=TestDatabaseSafetyInitializer.class) @SpringBootTest
class AdminGameModerationMySqlIntegrationTests {
    @Autowired AdminModerationService moderation; @Autowired GameRuntimeService runtime;
    @Autowired UserRepository users; @Autowired RoomRepository rooms; @Autowired RoomPlayerRepository members;
    @Autowired GameSessionRepository sessions; @Autowired JdbcTemplate jdbc;
    final List<Long> userIds=new ArrayList<>(),roomIds=new ArrayList<>(),sessionIds=new ArrayList<>();

    @AfterEach void cleanup(){
        for(long id:userIds)jdbc.update("DELETE FROM admin_audit_log WHERE admin_user_id=?",id);
        for(long id:sessionIds)jdbc.update("DELETE FROM game_sessions WHERE id=?",id);
        for(long id:roomIds)jdbc.update("DELETE FROM rooms WHERE id=?",id);
        for(long id:userIds)jdbc.update("DELETE FROM users WHERE id=?",id);
    }

    @Test void betweenHandTerminationFinishesAndCashOutsExactlyOnce(){
        Fixture f=fixture();GameRuntimeView completed=fold(f.started());assertThat(completed.handCompleted()).isTrue();
        var response=moderation.terminate(f.admin().getId(),completed.gameSessionId(),"between hands",null);
        assertThat(response.status()).isEqualTo("FINISHED");assertThat(response.deferred()).isFalse();
        assertFinishedOnce(f);assertThat(audits("GAME_TERMINATED",completed.gameSessionId())).isOne();
    }

    @Test void terminationGenuinelyRacesLegalActionWithoutDuplicateSettlementOrDeadlock()throws Exception{
        Fixture f=fixture();CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()){
            Future<?> terminate=executor.submit(()->{ready.countDown();await(go);moderation.terminate(f.admin().getId(),f.started().gameSessionId(),"concurrent",null);});
            Future<?> action=executor.submit(()->{ready.countDown();await(go);fold(f.started());});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();terminate.get(10,TimeUnit.SECONDS);action.get(10,TimeUnit.SECONDS);
        }
        assertFinishedOnce(f);
        long handId=jdbc.queryForObject("SELECT id FROM poker_hands WHERE game_session_id=?",Long.class,f.started().gameSessionId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_actions WHERE poker_hand_id=?",Long.class,handId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hand_players WHERE poker_hand_id=?",Long.class,handId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pot_awards pa JOIN pots p ON p.id=pa.pot_id WHERE p.poker_hand_id=?",Long.class,handId)).isOne();
        assertThat(audits("GAME_TERMINATED",f.started().gameSessionId())).isOne();
    }

    @Test void activeHandRemovalPreservesCommitmentAndDefersCashOutUntilSettlement(){
        Fixture f=fixture();long removed=f.second().getId();long committed=f.started().players().stream().filter(p->p.userId()==removed).findFirst().orElseThrow().totalCommitted();
        var response=moderation.removePlayer(f.admin().getId(),f.room().getId(),removed,"remove",null);
        assertThat(response.deferred()).isTrue();
        RoomPlayerEntity pending=members.findByRoomIdAndUserId(f.room().getId(),removed).orElseThrow();
        assertThat(pending.getPlayerState()).isEqualTo(RoomPlayerState.LEAVING);assertThat(pending.getTableChips()).isPositive();
        assertThat(users.findById(removed).orElseThrow().getAccountChips()).isEqualTo(4000);
        assertThat(f.started().players().stream().filter(p->p.userId()==removed).findFirst().orElseThrow().totalCommitted()).isEqualTo(committed);
        fold(f.started());
        RoomPlayerEntity departed=members.findByRoomIdAndUserId(f.room().getId(),removed).orElseThrow();
        assertThat(departed.isActive()).isFalse();assertThat(departed.getTableChips()).isZero();assertThat(departed.getSeatNumber()).isNull();
        assertThat(audits("PLAYER_REMOVED_FROM_ROOM",f.room().getId())).isOne();
    }

    @Test void durableRequestedAuditPreventsReplacementRuntimeAfterProcessLoss(){
        UserEntity admin=user(Role.ADMIN,5000),first=user(Role.PLAYER,4000),second=user(Role.PLAYER,4000);
        RoomEntity room=rooms.saveAndFlush(new RoomEntity("dur9-"+UUID.randomUUID(),first.getId(),RoomType.PUBLIC,null,6,50,100,1000,RoomStatus.WAITING,Instant.now()));roomIds.add(room.getId());
        members.saveAndFlush(new RoomPlayerEntity(room.getId(),first.getId(),1,RoomPlayerState.READY,1000));members.saveAndFlush(new RoomPlayerEntity(room.getId(),second.getId(),2,RoomPlayerState.READY,1000));
        GameSessionEntity session=sessions.saveAndFlush(new GameSessionEntity(room.getId(),GameSessionStatus.ACTIVE,Instant.now(),null));sessionIds.add(session.getId());
        jdbc.update("INSERT INTO admin_audit_log(admin_user_id,action_type,target_type,target_id,metadata_json,created_at) VALUES(?,?,?,?,?,NOW(6))",admin.getId(),"GAME_TERMINATED","GAME_SESSION",session.getId(),"{\"terminationStatus\":\"REQUESTED\",\"deferred\":true}");
        assertThatThrownBy(()->runtime.startGame(room.getId())).isInstanceOfSatisfying(GameRuntimeException.class,
                ex->assertThat(ex.code()).isEqualTo("ADMIN_TERMINATION_REQUESTED"));
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.ACTIVE);
    }

    private Fixture fixture(){UserEntity admin=user(Role.ADMIN,5000),first=user(Role.PLAYER,4000),second=user(Role.PLAYER,4000);RoomEntity room=rooms.saveAndFlush(new RoomEntity("gm9-"+UUID.randomUUID(),first.getId(),RoomType.PUBLIC,null,6,50,100,1000,RoomStatus.WAITING,Instant.now()));roomIds.add(room.getId());members.saveAndFlush(new RoomPlayerEntity(room.getId(),first.getId(),1,RoomPlayerState.READY,1000));members.saveAndFlush(new RoomPlayerEntity(room.getId(),second.getId(),2,RoomPlayerState.READY,1000));GameRuntimeView started=runtime.startGame(room.getId());sessionIds.add(started.gameSessionId());return new Fixture(admin,first,second,room,started);}
    private GameRuntimeView fold(GameRuntimeView view){return runtime.applyAction(view.gameId(),view.currentTurnUserId(),new GameActionIntent(view.turnId(),UUID.randomUUID(),PokerActionType.FOLD,0));}
    private void assertFinishedOnce(Fixture f){assertThat(sessions.findById(f.started().gameSessionId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.FINISHED);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM poker_hands WHERE game_session_id=?",Long.class,f.started().gameSessionId())).isOne();assertThatThrownBy(()->runtime.startNextHand(f.started().gameId())).isInstanceOf(GameRuntimeException.class);assertThat(members.findAllByRoomIdAndLeftAtIsNullOrderById(f.room().getId())).isEmpty();assertThat(jdbc.queryForObject("SELECT SUM(account_chips) FROM users WHERE id IN (?,?)",Long.class,f.first().getId(),f.second().getId())).isEqualTo(10000);assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(table_chips),0) FROM room_players WHERE room_id=?",Long.class,f.room().getId())).isZero();}
    private long audits(String action,long target){return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE action_type=? AND target_id=?",Long.class,action,target);}
    private UserEntity user(Role role,long chips){String s=UUID.randomUUID().toString().replace("-","").substring(0,10);UserEntity u=users.saveAndFlush(new UserEntity("gm9_"+s,"hash","gm9_"+s+"@example.test",role,AccountStatus.ACTIVE,chips));userIds.add(u.getId());return u;}
    private static void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}}
    private record Fixture(UserEntity admin,UserEntity first,UserEntity second,RoomEntity room,GameRuntimeView started){}
}
