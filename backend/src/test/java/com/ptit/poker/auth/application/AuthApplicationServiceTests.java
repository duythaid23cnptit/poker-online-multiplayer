package com.ptit.poker.auth.application;

import com.ptit.poker.auth.api.dto.LoginRequest;
import com.ptit.poker.auth.api.dto.RegisterRequest;
import com.ptit.poker.auth.application.exception.AccountLockedException;
import com.ptit.poker.auth.application.exception.AuthenticationFailedException;
import com.ptit.poker.auth.application.exception.DuplicateAccountException;
import com.ptit.poker.auth.application.exception.InvalidRefreshTokenException;
import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.infrastructure.persistence.RefreshTokenEntity;
import com.ptit.poker.auth.infrastructure.persistence.RefreshTokenRepository;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.auth.infrastructure.security.OpaqueRefreshTokenService;
import com.ptit.poker.player.api.dto.CurrentUserResponse;
import com.ptit.poker.player.application.PlayerProfileApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTests {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PlayerProfileApplicationService profileService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock OpaqueRefreshTokenService refreshTokenService;

    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AuthApplicationService(
                userRepository, refreshTokenRepository, profileService,
                passwordEncoder, jwtService, refreshTokenService);
    }

    @Test
    void registerHashesPasswordAndCreatesProfileWithSafeDefaults() {
        RegisterRequest request = new RegisterRequest(
                "alice", "correct-password", "Alice@Example.test", "Alice");
        UserEntity saved = org.mockito.Mockito.mock(UserEntity.class);
        when(saved.getId()).thenReturn(5L);
        when(passwordEncoder.encode("correct-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);
        CurrentUserResponse expected = org.mockito.Mockito.mock(CurrentUserResponse.class);
        when(profileService.currentUser(5L)).thenReturn(expected);

        assertThat(service.register(request)).isSameAs(expected);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("alice@example.test");
        assertThat(userCaptor.getValue().getAccountChips()).isZero();
        assertThat(userCaptor.getValue().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(profileService).createForUser(5L, "Alice");
    }

    @Test
    void duplicateUsernameIsRejectedBeforeWriting() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("alice", "password-123", null, "Alice");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(DuplicateAccountException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginIssuesTokensAndPersistsOnlyRefreshHash() {
        UserEntity user = activeUser(8L, "stored-hash");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);
        when(refreshTokenService.generate()).thenReturn("raw-refresh");
        when(refreshTokenService.hash("raw-refresh")).thenReturn("hashed-refresh");
        when(jwtService.refreshTokenExpiresAt()).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));
        when(jwtService.createAccessToken(user)).thenReturn("access-jwt");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        var response = service.login(new LoginRequest("alice", "password"));

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh");
        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        verify(refreshTokenService).hash("raw-refresh");
        assertThat(tokenCaptor.getValue().getUserId()).isEqualTo(8L);
    }

    @Test
    void wrongPasswordAndUnknownUserUseSameFailureType() {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest("unknown", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void lockedAccountCannotLogin() {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        when(user.getAccountStatus()).thenReturn(AccountStatus.LOCKED);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "password")))
                .isInstanceOf(AccountLockedException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refreshRejectsUnknownExpiredAndRevokedTokens() {
        when(refreshTokenService.hash(any())).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new RefreshTokenEntity(1L, "hash", Instant.EPOCH)));

        assertThatThrownBy(() -> service.refresh("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.refresh("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        RefreshTokenEntity revoked = new RefreshTokenEntity(1L, "hash", Instant.now().plusSeconds(60));
        revoked.revoke(Instant.now());
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> service.refresh("revoked"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesOnlyTokenOwnedByPrincipal() {
        RefreshTokenEntity token = new RefreshTokenEntity(4L, "hash", Instant.now().plusSeconds(60));
        when(refreshTokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        service.logout(4L, "raw");

        assertThat(token.getRevokedAt()).isNotNull();
    }

    private static UserEntity activeUser(Long id, String passwordHash) {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getPasswordHash()).thenReturn(passwordHash);
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        return user;
    }
}
