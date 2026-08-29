package com.ptit.poker.admin.infrastructure;

import com.ptit.poker.game.application.runtime.AdministrativeGameControlPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component @Profile("!bootstrap")
public class JdbcAdministrativeGameControlAdapter implements AdministrativeGameControlPort {
    private final JdbcTemplate jdbc;
    public JdbcAdministrativeGameControlAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean hasPendingTerminationForRoom(long roomId){
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM game_sessions gs JOIN admin_audit_log aal "
                + "ON aal.target_type='GAME_SESSION' AND aal.target_id=gs.id AND aal.action_type='GAME_TERMINATED' "
                + "WHERE gs.room_id=? AND gs.status='ACTIVE' AND JSON_UNQUOTE(JSON_EXTRACT(aal.metadata_json,'$.terminationStatus'))='REQUESTED')",
                Boolean.class,roomId);
    }
}
