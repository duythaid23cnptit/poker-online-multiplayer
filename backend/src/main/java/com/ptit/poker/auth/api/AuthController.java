package com.ptit.poker.auth.api;

import com.ptit.poker.auth.api.dto.AccessTokenResponse;
import com.ptit.poker.auth.api.dto.AuthResponse;
import com.ptit.poker.auth.api.dto.LoginRequest;
import com.ptit.poker.auth.api.dto.LogoutRequest;
import com.ptit.poker.auth.api.dto.RefreshTokenRequest;
import com.ptit.poker.auth.api.dto.RegisterRequest;
import com.ptit.poker.auth.application.AuthApplicationService;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.player.api.dto.CurrentUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authService;

    public AuthController(AuthApplicationService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ResponseEntity<CurrentUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    AccessTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal,
                                @Valid @RequestBody LogoutRequest request) {
        authService.logout(principal.userId(), request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
