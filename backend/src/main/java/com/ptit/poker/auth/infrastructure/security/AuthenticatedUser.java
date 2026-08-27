package com.ptit.poker.auth.infrastructure.security;

import com.ptit.poker.auth.domain.Role;
import java.security.Principal;

public record AuthenticatedUser(Long userId, String username, Role role) implements Principal {
    @Override public String getName() { return username; }
}
