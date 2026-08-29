package com.ptit.poker.admin.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.*;

class AdminModerationServiceTests {
    AdminUserModerationPort users=mock(AdminUserModerationPort.class);
    AdminRoomModerationPort rooms=mock(AdminRoomModerationPort.class);
    AdminGameModerationPort games=mock(AdminGameModerationPort.class);
    AdminAuditPort audit=mock(AdminAuditPort.class);
    AdminModerationService service=new AdminModerationService(users,rooms,games,audit);

    @Test void selfSuspendIsRejectedBeforeMutation(){
        assertThatThrownBy(()->service.suspend(7,7,"reason",null)).isInstanceOf(AdminModerationException.class)
                .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        verifyNoInteractions(users,audit);
    }
    @Test void unchangedSuspensionDoesNotDuplicateAudit(){
        when(users.suspend(8)).thenReturn(new AdminUserModerationPort.Change(false,"LOCKED","LOCKED"));
        assertThat(service.suspend(7,8,null,null).changed()).isFalse(); verifyNoInteractions(audit);
    }
    @Test void changedSuspensionIsAudited(){
        when(users.suspend(8)).thenReturn(new AdminUserModerationPort.Change(true,"ACTIVE","LOCKED"));
        assertThat(service.suspend(7,8,"  moderation  ","request-1").changed()).isTrue();
        verify(audit).record(7,AdminActionType.USER_SUSPENDED,AdminTargetType.USER,8L,"moderation","request-1",
                Map.of("previousStatus","ACTIVE","newStatus","LOCKED"));
    }
    @Test void blankReasonBecomesNullAndLongReasonIsRejected(){
        assertThat(AdminModerationService.reason("   ")).isNull();
        assertThatThrownBy(()->AdminModerationService.reason("x".repeat(501))).isInstanceOf(AdminModerationException.class);
    }
    @Test void activeGameRemovalIsReportedDeferredAndAuditedOnce(){
        when(rooms.removePlayer(11,8)).thenReturn(new AdminRoomModerationPort.Change(true,true,22L));
        var result=service.removePlayer(7,11,8,null,null);
        assertThat(result.deferred()).isTrue();
        verify(audit).record(eq(7L),eq(AdminActionType.PLAYER_REMOVED_FROM_ROOM),eq(AdminTargetType.ROOM),eq(11L),isNull(),isNull(),anyMap());
    }
    @Test void terminationRequestRecordsSafeBoundaryMetadata(){
        when(games.terminate(22)).thenReturn(new AdminGameModerationPort.Change(true,true));
        assertThat(service.terminate(7,22,"ops",null).status()).isEqualTo("TERMINATION_REQUESTED");
        verify(audit).record(eq(7L),eq(AdminActionType.GAME_TERMINATED),eq(AdminTargetType.GAME_SESSION),eq(22L),eq("ops"),isNull(),anyMap());
    }
}
