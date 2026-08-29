package com.ptit.poker.admin.application;

import static com.ptit.poker.admin.application.AdminActionType.*;
import static com.ptit.poker.admin.application.AdminTargetType.*;
import com.ptit.poker.admin.application.AdminModels.MutationResponse;
import com.ptit.poker.admin.application.AdminModels.Page;
import com.ptit.poker.admin.application.AdminModels.AuditItem;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

@Service
@Profile("!bootstrap")
public class AdminModerationService {
    private final AdminUserModerationPort users;
    private final AdminRoomModerationPort rooms;
    private final AdminGameModerationPort games;
    private final AdminAuditPort audit;

    public AdminModerationService(AdminUserModerationPort users, AdminRoomModerationPort rooms,
                                  AdminGameModerationPort games, AdminAuditPort audit) {
        this.users = users; this.rooms = rooms; this.games = games; this.audit = audit;
    }

    @Transactional
    public MutationResponse suspend(long adminId, long userId, String reason, String requestId) {
        if (adminId == userId) throw conflict("ADMIN_SELF_SUSPEND", "An administrator cannot suspend their own account");
        String normalized = reason(reason);
        var change = users.suspend(userId);
        if (change.changed()) audit.record(adminId, USER_SUSPENDED, USER, userId, normalized, requestId,
                Map.of("previousStatus", change.previousStatus(), "newStatus", change.currentStatus()));
        return new MutationResponse(change.currentStatus(), userId, change.changed(), false);
    }

    @Transactional
    public MutationResponse reactivate(long adminId, long userId, String reason, String requestId) {
        String normalized = reason(reason);
        var change = users.reactivate(userId);
        if (change.changed()) audit.record(adminId, USER_REACTIVATED, USER, userId, normalized, requestId,
                Map.of("previousStatus", change.previousStatus(), "newStatus", change.currentStatus()));
        return new MutationResponse(change.currentStatus(), userId, change.changed(), false);
    }

    @Transactional
    public MutationResponse removePlayer(long adminId, long roomId, long userId, String reason, String requestId) {
        String normalized = reason(reason);
        var change = rooms.removePlayer(roomId, userId);
        if (change.changed()) audit.record(adminId, PLAYER_REMOVED_FROM_ROOM, ROOM, roomId, normalized, requestId,
                Map.of("roomId", roomId, "affectedUserId", userId, "deferred", change.deferred()));
        return new MutationResponse(change.deferred() ? "REMOVAL_PENDING" : "REMOVED", roomId, change.changed(), change.deferred());
    }

    @Transactional
    public MutationResponse closeRoom(long adminId, long roomId, String reason, String requestId) {
        String normalized = reason(reason);
        var change = rooms.close(roomId);
        if (change.changed()) audit.record(adminId, ROOM_CLOSED, ROOM, roomId, normalized, requestId, Map.of("roomId", roomId));
        return new MutationResponse("CLOSED", roomId, change.changed(), false);
    }

    @Transactional
    public MutationResponse terminate(long adminId, long sessionId, String reason, String requestId) {
        String normalized = reason(reason);
        var change = games.terminate(sessionId);
        if (change.changed()) audit.record(adminId, GAME_TERMINATED, GAME_SESSION, sessionId, normalized, requestId,
                Map.of("gameSessionId", sessionId, "deferred", change.deferred(),
                        "terminationStatus", change.deferred() ? "REQUESTED" : "COMPLETED"));
        return new MutationResponse(change.deferred() ? "TERMINATION_REQUESTED" : "FINISHED", sessionId, change.changed(), change.deferred());
    }

    @Transactional(readOnly = true)
    public Page<AuditItem> audit(int page, int size, Long adminId, String action, String target, Long targetId,
                                 Instant from, Instant to) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to)))
            throw bad("INVALID_AUDIT_QUERY", "Audit pagination or date range is invalid");
        try {
            return audit.find(page, size, adminId, action == null ? null : AdminActionType.valueOf(action),
                    target == null ? null : AdminTargetType.valueOf(target), targetId, from, to);
        } catch (IllegalArgumentException ex) { throw bad("INVALID_AUDIT_FILTER", "Audit filters are invalid"); }
    }

    static String reason(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > 500) throw bad("INVALID_REASON", "Reason must not exceed 500 characters");
        return result;
    }
    private static AdminModerationException bad(String code, String message) { return new AdminModerationException(code, message, HttpStatus.BAD_REQUEST); }
    private static AdminModerationException conflict(String code, String message) { return new AdminModerationException(code, message, HttpStatus.CONFLICT); }
}
