package com.ptit.poker.admin.infrastructure;

import com.ptit.poker.admin.application.*;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.infrastructure.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!bootstrap")
public class RuntimeAdminGameModerationAdapter implements AdminGameModerationPort {
    private final GameRuntimeService runtime; private final GameSessionRepository sessions;
    public RuntimeAdminGameModerationAdapter(GameRuntimeService runtime, GameSessionRepository sessions){this.runtime=runtime;this.sessions=sessions;}
    @Override public Change terminate(long id){
        GameSessionEntity session=sessions.findById(id).orElseThrow(()->new AdminModerationException("GAME_SESSION_NOT_FOUND","Game session not found",HttpStatus.NOT_FOUND));
        if(session.getStatus()!=GameSessionStatus.ACTIVE)throw new AdminModerationException("GAME_ALREADY_FINISHED","Game session is not active",HttpStatus.CONFLICT);
        try {var result=runtime.requestAdministrativeTermination(id);return new Change(result.changed(),result.deferred());}
        catch(GameRuntimeException ex){throw new AdminModerationException("GAME_RUNTIME_NOT_ACTIVE","Active game runtime was not found",HttpStatus.CONFLICT);}
    }
}
