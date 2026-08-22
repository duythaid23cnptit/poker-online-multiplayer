package com.ptit.poker.player.application;

import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.player.api.dto.CurrentUserResponse;
import com.ptit.poker.player.api.dto.UpdateProfileRequest;
import com.ptit.poker.player.application.exception.ProfileNotFoundException;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileEntity;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class PlayerProfileApplicationService {

    private final PlayerProfileRepository profileRepository;
    private final UserRepository userRepository;

    public PlayerProfileApplicationService(PlayerProfileRepository profileRepository, UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    public PlayerProfileEntity createForUser(Long userId, String displayName) {
        return profileRepository.save(new PlayerProfileEntity(
                userId, displayName.trim(), null, PresenceStatus.OFFLINE));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(ProfileNotFoundException::new);
        PlayerProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(ProfileNotFoundException::new);
        return toResponse(user, profile);
    }

    @Transactional
    public CurrentUserResponse updateCurrentUser(Long userId, UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(userId).orElseThrow(ProfileNotFoundException::new);
        PlayerProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(ProfileNotFoundException::new);
        String avatarUrl = request.avatarUrl() == null || request.avatarUrl().isBlank()
                ? null : request.avatarUrl().trim();
        profile.update(request.displayName().trim(), avatarUrl);
        return toResponse(user, profile);
    }

    private static CurrentUserResponse toResponse(UserEntity user, PlayerProfileEntity profile) {
        return new CurrentUserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getAccountStatus(),
                user.getAccountChips(), profile.getDisplayName(), profile.getAvatarUrl(),
                profile.getOnlineStatus());
    }
}
