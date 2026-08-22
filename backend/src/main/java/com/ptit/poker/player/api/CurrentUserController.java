package com.ptit.poker.player.api;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.player.api.dto.CurrentUserResponse;
import com.ptit.poker.player.api.dto.UpdateProfileRequest;
import com.ptit.poker.player.application.PlayerProfileApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/me")
public class CurrentUserController {

    private final PlayerProfileApplicationService profileService;

    public CurrentUserController(PlayerProfileApplicationService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    CurrentUserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return profileService.currentUser(principal.userId());
    }

    @PatchMapping
    CurrentUserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateCurrentUser(principal.userId(), request);
    }
}
