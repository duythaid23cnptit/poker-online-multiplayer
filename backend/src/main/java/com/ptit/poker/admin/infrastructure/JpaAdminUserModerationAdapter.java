package com.ptit.poker.admin.infrastructure;

import com.ptit.poker.admin.application.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!bootstrap")
public class JpaAdminUserModerationAdapter implements AdminUserModerationPort {
    private final UserRepository users;
    public JpaAdminUserModerationAdapter(UserRepository users) { this.users = users; }

    @Override public Change suspend(long userId) {
        UserEntity user = require(userId); String previous = user.getAccountStatus().name();
        boolean changed = user.lock();
        return new Change(changed, previous, user.getAccountStatus().name());
    }
    @Override public Change reactivate(long userId) {
        UserEntity user = require(userId); String previous = user.getAccountStatus().name();
        boolean changed = user.reactivate();
        return new Change(changed, previous, user.getAccountStatus().name());
    }
    private UserEntity require(long id) {
        return users.findByIdForUpdate(id).orElseThrow(() ->
                new AdminModerationException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
    }
}
