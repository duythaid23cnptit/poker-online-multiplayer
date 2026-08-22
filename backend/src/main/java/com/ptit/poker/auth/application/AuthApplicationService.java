package com.ptit.poker.auth.application;

import com.ptit.poker.auth.api.dto.AccessTokenResponse;
import com.ptit.poker.auth.api.dto.AuthResponse;
import com.ptit.poker.auth.api.dto.LoginRequest;
import com.ptit.poker.auth.api.dto.RegisterRequest;
import com.ptit.poker.auth.application.exception.AccountLockedException;
import com.ptit.poker.auth.application.exception.AuthenticationFailedException;
import com.ptit.poker.auth.application.exception.DuplicateAccountException;
import com.ptit.poker.auth.application.exception.InvalidRefreshTokenException;
import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.RefreshTokenEntity;
import com.ptit.poker.auth.infrastructure.persistence.RefreshTokenRepository;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.auth.infrastructure.security.OpaqueRefreshTokenService;
import com.ptit.poker.player.api.dto.CurrentUserResponse;
import com.ptit.poker.player.application.PlayerProfileApplicationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@Profile("!bootstrap")
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlayerProfileApplicationService profileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OpaqueRefreshTokenService refreshTokenService;

    public AuthApplicationService(UserRepository userRepository,
                                  RefreshTokenRepository refreshTokenRepository,
                                  PlayerProfileApplicationService profileService,
                                  PasswordEncoder passwordEncoder,
                                  JwtService jwtService,
                                  OpaqueRefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.profileService = profileService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public CurrentUserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = normalizeEmail(request.email());
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateAccountException("username");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new DuplicateAccountException("email");
        }
        UserEntity user = userRepository.save(new UserEntity(
                username, passwordEncoder.encode(request.password()), email,
                Role.PLAYER, AccountStatus.ACTIVE, 0));
        profileService.createForUser(user.getId(), request.displayName());
        userRepository.flush();
        return profileService.currentUser(user.getId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(AuthenticationFailedException::new);
        ensureActive(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }
        user.recordLogin(Instant.now());
        String rawRefreshToken = refreshTokenService.generate();
        refreshTokenRepository.save(new RefreshTokenEntity(
                user.getId(), refreshTokenService.hash(rawRefreshToken), jwtService.refreshTokenExpiresAt()));
        return new AuthResponse(
                jwtService.createAccessToken(user), rawRefreshToken, "Bearer",
                jwtService.accessTokenExpiresInSeconds());
    }

    @Transactional(readOnly = true)
    public AccessTokenResponse refresh(String rawRefreshToken) {
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(refreshTokenService.hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!token.isUsableAt(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        UserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        ensureActive(user);
        return new AccessTokenResponse(
                jwtService.createAccessToken(user), "Bearer", jwtService.accessTokenExpiresInSeconds());
    }

    @Transactional
    public void logout(Long authenticatedUserId, String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(refreshTokenService.hash(rawRefreshToken))
                .filter(token -> token.getUserId().equals(authenticatedUserId))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private static void ensureActive(UserEntity user) {
        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            throw new AccountLockedException();
        }
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
