package com.ptit.poker.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.ptit.poker.auth.domain.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.room.domain.*;
import com.ptit.poker.room.infrastructure.persistence.*;
import com.ptit.poker.game.infrastructure.persistence.*;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_USERNAME",matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_PASSWORD",matches=".+")
@ActiveProfiles("test") @ContextConfiguration(initializers=TestDatabaseSafetyInitializer.class)
@SpringBootTest @AutoConfigureMockMvc
class AdminMutationMySqlIntegrationTests {
    @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired RoomRepository rooms;
    @Autowired RoomPlayerRepository members; @Autowired JwtService jwt; @Autowired JdbcTemplate jdbc;
    @Autowired GameSessionRepository sessions;
    final List<Long> userIds=new ArrayList<>(),roomIds=new ArrayList<>(),sessionIds=new ArrayList<>();

    @AfterEach void cleanup(){
        for(long id:userIds)jdbc.update("DELETE FROM admin_audit_log WHERE admin_user_id=? OR target_id=?",id,id);
        for(long id:roomIds)jdbc.update("DELETE FROM admin_audit_log WHERE target_type='ROOM' AND target_id=?",id);
        for(long id:sessionIds)jdbc.update("DELETE FROM game_sessions WHERE id=?",id);
        for(long id:roomIds)jdbc.update("DELETE FROM rooms WHERE id=?",id);
        for(long id:userIds)jdbc.update("DELETE FROM users WHERE id=?",id);
    }
    @Test void v9CreatesAppendOnlyAuditTableAndHibernateValidates(){
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='9' AND success=TRUE",Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='admin_audit_log'",Long.class)).isOne();
    }
    @Test void suspendReactivateAndIdempotencyAreAuditedWithoutSecrets()throws Exception{
        UserEntity admin=user(Role.ADMIN,5000),player=user(Role.PLAYER,5000);String token=bearer(admin);
        mvc.perform(post("/api/v1/admin/users/"+player.getId()+"/suspend").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" policy \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCKED")).andExpect(jsonPath("$.changed").value(true)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/v1/admin/users/"+player.getId()+"/suspend").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        assertThat(audits("USER_SUSPENDED",player.getId())).isOne();
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION,bearer(player))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/users/"+player.getId()+"/reactivate").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(audits("USER_REACTIVATED",player.getId())).isOne();
        mvc.perform(get("/api/v1/rankings/leaderboard").header(HttpHeaders.AUTHORIZATION,bearer(player))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/audit-log?adminUserId="+admin.getId()+"&targetType=USER&targetId="+player.getId()).header(HttpHeaders.AUTHORIZATION,token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].actionType").value("USER_REACTIVATED"))
                .andExpect(jsonPath("$.items[1].actionType").value("USER_SUSPENDED"));
    }
    @Test void selfSuspendAndSecurityFailuresCreateNoAudit()throws Exception{
        UserEntity admin=user(Role.ADMIN,5000),player=user(Role.PLAYER,5000);String path="/api/v1/admin/users/"+admin.getId()+"/suspend";
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION,bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isConflict());
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION,bearer(player)).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());
        assertThat(users.findById(admin.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(audits("USER_SUSPENDED",admin.getId())).isZero();
    }
    @Test void idleSeatedRemovalCashOutsExactlyOnceAndWritesOneAudit()throws Exception{
        UserEntity admin=user(Role.ADMIN,5000),player=user(Role.PLAYER,4000);RoomEntity room=room(admin);
        members.saveAndFlush(new RoomPlayerEntity(room.getId(),player.getId(),1,RoomPlayerState.NOT_READY,1000));
        String path="/api/v1/admin/rooms/"+room.getId()+"/players/"+player.getId()+"/remove",token=bearer(admin);
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true));
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        assertThat(users.findById(player.getId()).orElseThrow().getAccountChips()).isEqualTo(5000);
        assertThat(members.findByRoomIdAndUserId(room.getId(),player.getId()).orElseThrow().getTableChips()).isZero();
        assertThat(audits("PLAYER_REMOVED_FROM_ROOM",room.getId())).isOne();
    }
    @Test void closingIdleRoomFinalizesParticipantsAndAuditApiIsAdminOnly()throws Exception{
        UserEntity admin=user(Role.ADMIN,5000),player=user(Role.PLAYER,4000);RoomEntity room=room(admin);
        members.saveAndFlush(new RoomPlayerEntity(room.getId(),player.getId(),null,RoomPlayerState.SPECTATING,0));
        mvc.perform(post("/api/v1/admin/rooms/"+room.getId()+"/close").header(HttpHeaders.AUTHORIZATION,bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"operations\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/api/v1/admin/rooms/"+room.getId()+"/close").header(HttpHeaders.AUTHORIZATION,bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.CLOSED);
        String from=Instant.now().minusSeconds(60).toString();
        String to=Instant.now().plusSeconds(60).toString();
        mvc.perform(get("/api/v1/admin/audit-log")
                        .queryParam("actionType","ROOM_CLOSED").queryParam("targetType","ROOM")
                        .queryParam("targetId",room.getId().toString()).queryParam("adminUserId",admin.getId().toString())
                        .queryParam("from",from).queryParam("to",to).queryParam("page","0").queryParam("size","1")
                        .header(HttpHeaders.AUTHORIZATION,bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].reason").value("operations")).andExpect(jsonPath("$.items[0].metadata.passwordHash").doesNotExist());
        assertThat(audits("ROOM_CLOSED",room.getId())).isOne();
        mvc.perform(get("/api/v1/admin/audit-log").header(HttpHeaders.AUTHORIZATION,bearer(player))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit-log")).andExpect(status().isUnauthorized());
    }
    @Test void activeGameRoomCloseIsConflictWithoutMutationOrAudit()throws Exception{
        UserEntity admin=user(Role.ADMIN,5000),owner=user(Role.PLAYER,4000);RoomEntity room=room(owner);
        GameSessionEntity session=sessions.saveAndFlush(new GameSessionEntity(room.getId(),GameSessionStatus.ACTIVE,Instant.now(),null));sessionIds.add(session.getId());
        mvc.perform(post("/api/v1/admin/rooms/"+room.getId()+"/close").header(HttpHeaders.AUTHORIZATION,bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.ACTIVE);
        assertThat(audits("ROOM_CLOSED",room.getId())).isZero();
    }
    private long audits(String action,long target){return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE action_type=? AND target_id=?",Long.class,action,target);}
    private UserEntity user(Role role,long chips){String s=UUID.randomUUID().toString().replace("-","").substring(0,10);UserEntity u=users.saveAndFlush(new UserEntity("m9_"+s,"hash","m9_"+s+"@example.test",role,AccountStatus.ACTIVE,chips));userIds.add(u.getId());return u;}
    private RoomEntity room(UserEntity owner){RoomEntity r=rooms.saveAndFlush(new RoomEntity("mod-"+UUID.randomUUID(),owner.getId(),RoomType.PUBLIC,null,6,50,100,1000,RoomStatus.WAITING,Instant.now()));roomIds.add(r.getId());return r;}
    private String bearer(UserEntity user){return "Bearer "+jwt.createAccessToken(user);}
}
