package com.ptit.poker.game.api.realtime;

import static org.mockito.Mockito.*;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.domain.betting.PokerActionType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class GameMessagingControllerTests {
    @Test void authenticatedPrincipalIsTheOnlyActingIdentity() {
        GameRealtimeApplicationService games=mock(GameRealtimeApplicationService.class);
        GameMessagingController controller=new GameMessagingController(games);
        UUID gameId=UUID.randomUUID();
        var message=new GameActionMessage(PokerActionType.CALL,null,UUID.randomUUID(),UUID.randomUUID());
        var principal=new AuthenticatedUser(42L,"alice",Role.PLAYER);
        controller.action(gameId,message,new UsernamePasswordAuthenticationToken(principal,null));
        verify(games).handleAction(gameId,42L,message);
    }
}
