package com.ptit.poker.analytics.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import com.ptit.poker.analytics.application.PlayerStatisticsService;
import com.ptit.poker.analytics.infrastructure.persistence.*;
import com.ptit.poker.auth.domain.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import com.ptit.poker.game.application.GameSessionPersistenceService;
import com.ptit.poker.game.domain.pot.PotType;
import com.ptit.poker.game.domain.state.*;
import com.ptit.poker.game.infrastructure.persistence.*;
import com.ptit.poker.room.domain.*;
import com.ptit.poker.room.infrastructure.persistence.*;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
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
class PlayerStatisticsMySqlIntegrationTests {
    @Autowired UserRepository users;@Autowired RoomRepository rooms;@Autowired GameSessionRepository sessions;
    @Autowired PokerHandRepository hands;@Autowired HandPlayerRepository handPlayers;@Autowired PotRepository pots;
    @Autowired PotAwardRepository awards;@Autowired PlayerStatisticsRepository statistics;
    @Autowired GameSessionPersistenceService sessionService;@Autowired PlayerStatisticsService service;@Autowired JdbcTemplate jdbc;
    private Long fixtureUserId,fixtureRoomId,fixtureSessionId;
    @AfterEach void cleanup(){if(fixtureSessionId!=null)jdbc.update("DELETE FROM game_sessions WHERE id=?",fixtureSessionId);
        if(fixtureRoomId!=null)jdbc.update("DELETE FROM rooms WHERE id=?",fixtureRoomId);
        if(fixtureUserId!=null)jdbc.update("DELETE FROM users WHERE id=?",fixtureUserId);}
    @Test void completedSessionAutomaticallyCreatesIdempotentAuthoritativeProjection(){Instant start=Instant.parse("2026-08-28T00:00:00Z");
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10);UserEntity user=users.saveAndFlush(new UserEntity(
                "s8_"+suffix,"hash","s8_"+suffix+"@example.test",Role.PLAYER,AccountStatus.ACTIVE,5000));
        fixtureUserId=user.getId();
        RoomEntity room=rooms.saveAndFlush(new RoomEntity("stats-"+suffix,user.getId(),RoomType.PUBLIC,null,6,50,100,1000,RoomStatus.PLAYING,start));
        fixtureRoomId=room.getId();
        GameSessionEntity session=sessions.saveAndFlush(new GameSessionEntity(room.getId(),GameSessionStatus.ACTIVE,start,null));
        fixtureSessionId=session.getId();
        PokerHandEntity hand=hands.saveAndFlush(new PokerHandEntity(session.getId(),1,1,1,2,50,100,start,start.plusSeconds(60),
                GamePhase.FINISHED,"",HandEndReason.ALL_OTHERS_FOLDED));
        handPlayers.saveAndFlush(new HandPlayerEntity(hand.getId(),user.getId(),1,1000,1100,100,PokerPlayerState.ACTIVE,true,false,"AS,KH"));
        PotEntity pot=pots.saveAndFlush(new PotEntity(hand.getId(),0,PotType.MAIN,150,100));
        awards.saveAndFlush(new PotAwardEntity(pot.getId(),user.getId(),150,0));
        sessionService.finishSession(session.getId());
        assertThat(jdbc.queryForList("""
                SELECT DISTINCT gs.id FROM game_sessions gs JOIN poker_hands ph ON ph.game_session_id=gs.id
                JOIN hand_players hp ON hp.poker_hand_id=ph.id
                WHERE hp.user_id=? AND gs.status='FINISHED' AND ph.ended_at IS NOT NULL
                """,Long.class,user.getId())).containsExactly(session.getId());
        var value=service.get(user.getId());assertThat(value.totalGames()).isOne();assertThat(value.totalHands()).isOne();
        assertThat(value.totalWins()).isOne();assertThat(value.totalLosses()).isZero();assertThat(value.winRate()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(value.totalChipsWon()).isEqualTo(100);assertThat(value.totalChipsLost()).isZero();assertThat(value.netChip()).isEqualTo(100);
        assertThat(value.largestPotWon()).isEqualTo(150);assertThat(value.averagePlayingSeconds()).isEqualTo(60);
        service.refreshPlayerStatistics(user.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_statistics WHERE user_id=?",Long.class,user.getId())).isOne();
        assertThat(service.get(user.getId())).isEqualTo(value);
    }
}
