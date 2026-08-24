package com.ptit.poker.player.infrastructure.account;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.player.application.RoomPlayerAccountPort;
import com.ptit.poker.player.application.exception.InsufficientAccountChipsException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class JpaRoomPlayerAccountAdapter implements RoomPlayerAccountPort {
    private final UserRepository users;

    public JpaRoomPlayerAccountAdapter(UserRepository users) { this.users = users; }

    @Override public void requireActive(Long userId) { active(userId); }

    @Override public String username(Long userId) {
        return users.findById(userId).map(UserEntity::getUsername).orElse("unknown");
    }

    @Override public void debit(Long userId, long amount) {
        UserEntity user = users.findByIdForUpdate(userId).filter(value -> value.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Account is unavailable"));
        if (user.getAccountChips() < amount) throw new InsufficientAccountChipsException();
        user.debitAccountChips(amount);
    }

    @Override public void credit(Long userId, long amount) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new IllegalStateException("Account is unavailable"))
                .creditAccountChips(amount);
    }

    private UserEntity active(Long userId) {
        return users.findById(userId).filter(value -> value.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Account is unavailable"));
    }
}
