package com.ptit.poker.auth.infrastructure.security;

import com.ptit.poker.auth.domain.Role;

public record AuthenticatedUser(Long userId, String username, Role role) {
}

