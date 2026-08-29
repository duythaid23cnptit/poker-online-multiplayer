package com.ptit.poker.admin.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ptit.poker.auth.domain.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_USERNAME",matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_PASSWORD",matches=".+")
@ActiveProfiles("test") @ContextConfiguration(initializers=TestDatabaseSafetyInitializer.class) @SpringBootTest
class AdminAuditRollbackMySqlIntegrationTests {
    @Autowired AdminModerationService moderation; @Autowired UserRepository users; @Autowired JdbcTemplate jdbc;
    @MockitoBean AdminAuditPort audit;
    Long adminId,targetId;

    @AfterEach void cleanup(){if(adminId!=null)jdbc.update("DELETE FROM admin_audit_log WHERE admin_user_id=?",adminId);if(targetId!=null)jdbc.update("DELETE FROM users WHERE id=?",targetId);if(adminId!=null)jdbc.update("DELETE FROM users WHERE id=?",adminId);}

    @Test void auditFailureRollsBackUserSuspensionAtomically(){
        adminId=user(Role.ADMIN).getId();targetId=user(Role.PLAYER).getId();
        doThrow(new IllegalStateException("forced audit failure")).when(audit).record(anyLong(),any(),any(),anyLong(),any(),any(),anyMap());
        assertThatThrownBy(()->moderation.suspend(adminId,targetId,"rollback proof",null))
                .isInstanceOf(IllegalStateException.class).hasMessage("forced audit failure");
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE id=?",String.class,targetId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE action_type='USER_SUSPENDED' AND target_id=?",Long.class,targetId)).isZero();
    }
    private UserEntity user(Role role){String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10);return users.saveAndFlush(new UserEntity("rb9_"+suffix,"hash","rb9_"+suffix+"@example.test",role,AccountStatus.ACTIVE,5000));}
}
