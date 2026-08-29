package com.ptit.poker.admin.application;

import java.time.Instant;
import java.util.Map;
import com.ptit.poker.admin.application.AdminModels.Page;

public interface AdminAuditPort {
    void record(long adminUserId, AdminActionType actionType, AdminTargetType targetType, Long targetId,
                String reason, String requestId, Map<String, Object> metadata);
    Page<AdminModels.AuditItem> find(int page, int size, Long adminUserId, AdminActionType actionType,
                                    AdminTargetType targetType, Long targetId, Instant from, Instant to);
}
