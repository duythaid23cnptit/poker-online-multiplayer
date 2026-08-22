package com.ptit.poker.player.api.dto;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.player.domain.PresenceStatus;

public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        Role role,
        AccountStatus accountStatus,
        long accountChips,
        String displayName,
        String avatarUrl,
        PresenceStatus onlineStatus) {
}
