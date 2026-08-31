package com.ptit.poker.social.infrastructure.player;

import com.ptit.poker.social.application.SocialPlayerQueryPort;
import com.ptit.poker.player.domain.PresenceStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Profile("!bootstrap")
class JdbcSocialPlayerQueryAdapter implements SocialPlayerQueryPort {
    private final JdbcTemplate jdbc;

    JdbcSocialPlayerQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SafePlayerSummary> findActivePlayer(long userId) {
        return jdbc.query("""
                SELECT u.id, p.display_name, p.avatar_url
                FROM users u JOIN player_profiles p ON p.user_id=u.id
                WHERE u.id=? AND u.account_status='ACTIVE'
                """, (result, row) -> new SafePlayerSummary(
                result.getLong("id"), result.getString("display_name"), result.getString("avatar_url")), userId)
                .stream().findFirst();
    }

    @Override
    public Map<Long, SafePlayerSummary> findSafePlayerSummaries(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<Long> distinctIds = userIds.stream().distinct().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(distinctIds.size(), "?"));
        List<SafePlayerSummary> summaries = jdbc.query("""
                        SELECT u.id, p.display_name, p.avatar_url
                        FROM users u JOIN player_profiles p ON p.user_id=u.id
                        WHERE u.id IN (
                        """ + placeholders + ")",
                (result, row) -> new SafePlayerSummary(
                        result.getLong("id"), result.getString("display_name"), result.getString("avatar_url")),
                distinctIds.toArray());
        Map<Long, SafePlayerSummary> byId = new LinkedHashMap<>();
        summaries.forEach(summary -> byId.put(summary.userId(), summary));
        return Map.copyOf(byId);
    }

    @Override
    public Map<Long, PresenceStatus> findPresenceStatuses(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<Long> distinctIds = userIds.stream().distinct().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(distinctIds.size(), "?"));
        Map<Long, PresenceStatus> byId = new LinkedHashMap<>();
        jdbc.query("SELECT user_id, online_status FROM player_profiles WHERE user_id IN (" + placeholders + ")",
                (RowCallbackHandler) result -> byId.put(result.getLong("user_id"),
                        PresenceStatus.valueOf(result.getString("online_status"))), distinctIds.toArray());
        return Map.copyOf(byId);
    }
}
