package com.ptit.poker.social.presence.infrastructure.persistence;

import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.social.presence.application.PresencePersistencePort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("!bootstrap")
class JdbcPresencePersistenceAdapter implements PresencePersistencePort {
    private final JdbcTemplate jdbc;

    JdbcPresencePersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean transition(long userId, PresenceStatus status) {
        return jdbc.update("""
                UPDATE player_profiles SET online_status=?
                WHERE user_id=? AND online_status<>?
                """, status.name(), userId, status.name()) == 1;
    }

    @Override
    public Optional<PresenceStatus> find(long userId) {
        return jdbc.query("SELECT online_status FROM player_profiles WHERE user_id=?",
                (result, row) -> PresenceStatus.valueOf(result.getString(1)), userId).stream().findFirst();
    }

    @Override
    public int resetAllOffline() {
        return jdbc.update("UPDATE player_profiles SET online_status='OFFLINE' WHERE online_status<>'OFFLINE'");
    }
}
