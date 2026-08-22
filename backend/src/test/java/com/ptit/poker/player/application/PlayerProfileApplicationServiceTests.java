package com.ptit.poker.player.application;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.player.api.dto.UpdateProfileRequest;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileEntity;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerProfileApplicationServiceTests {

    private final PlayerProfileRepository profileRepository = mock(PlayerProfileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlayerProfileApplicationService service =
            new PlayerProfileApplicationService(profileRepository, userRepository);

    @Test
    void currentUserCombinesOnlySafeAccountAndProfileFields() {
        UserEntity user = user();
        PlayerProfileEntity profile = profile();
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(12L)).thenReturn(Optional.of(profile));

        var response = service.currentUser(12L);

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.accountChips()).isZero();
    }

    @Test
    void updateChangesOnlyProfileFieldsForPrincipalUserId() {
        UserEntity user = user();
        PlayerProfileEntity profile = profile();
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(12L)).thenReturn(Optional.of(profile));

        var response = service.updateCurrentUser(
                12L, new UpdateProfileRequest("Updated", " https://example.test/avatar.png "));

        assertThat(response.displayName()).isEqualTo("Updated");
        assertThat(response.avatarUrl()).isEqualTo("https://example.test/avatar.png");
        verify(userRepository).findById(12L);
        verify(profileRepository).findByUserId(12L);
    }

    private static UserEntity user() {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(12L);
        when(user.getUsername()).thenReturn("alice");
        when(user.getRole()).thenReturn(Role.PLAYER);
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        return user;
    }

    private static PlayerProfileEntity profile() {
        return new PlayerProfileEntity(12L, "Alice", null, PresenceStatus.OFFLINE);
    }
}

