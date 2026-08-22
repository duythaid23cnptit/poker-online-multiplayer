package com.ptit.poker.auth.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.RefreshTokenEntity;
import com.ptit.poker.auth.infrastructure.persistence.RefreshTokenRepository;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.OpaqueRefreshTokenService;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiMySqlIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PlayerProfileRepository profileRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired OpaqueRefreshTokenService refreshTokenService;

    @Test
    void registrationCreatesAtomicSafeAccountAndProfile() throws Exception {
        String username = unique("register");
        String email = username + "@example.test";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.accountChips").value(0))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        UserEntity user = userRepository.findByUsername(username).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("correct-password");
        assertThat(passwordEncoder.matches("correct-password", user.getPasswordHash())).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.PLAYER);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(profileRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    void registrationRejectsDuplicateUsernameEmailAndInvalidInput() throws Exception {
        String username = unique("duplicate");
        String email = username + "@example.test";
        register(username, email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, unique("other") + "@example.test")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(unique("other"), email)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void loginIssuesTokensWithoutSensitiveFieldsAndRejectsUnsafeCredentials() throws Exception {
        String username = unique("login");
        register(username, null);

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "correct-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String rawRefreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenService.hash(rawRefreshToken))).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(rawRefreshToken)).isEmpty();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(unique("unknown"), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void lockedAccountCannotLogin() throws Exception {
        String username = unique("locked");
        userRepository.saveAndFlush(new UserEntity(
                username, passwordEncoder.encode("correct-password"), null,
                Role.PLAYER, AccountStatus.LOCKED, 0));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "correct-password")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void accessTokenProtectsCurrentProfileAndUsesPrincipalIdentity() throws Exception {
        String username = unique("profile");
        register(username, null);
        Tokens tokens = login(username);

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void authenticatedUserUpdatesOnlyOwnEditableProfileFields() throws Exception {
        String username = unique("update");
        register(username, null);
        Tokens tokens = login(username);

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Name\",\"avatarUrl\":\"https://example.test/a.png\",\"accountChips\":999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Name"))
                .andExpect(jsonPath("$.accountChips").value(0));
    }

    @Test
    void refreshHonorsUnknownExpiredRevokedAndLogoutStates() throws Exception {
        String username = unique("refresh");
        register(username, null);
        Tokens tokens = login(username);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson("unknown-token")))
                .andExpect(status().isUnauthorized());

        UserEntity user = userRepository.findByUsername(username).orElseThrow();
        String expiredRaw = "expired-" + UUID.randomUUID();
        refreshTokenRepository.saveAndFlush(new RefreshTokenEntity(
                user.getId(), refreshTokenService.hash(expiredRaw), Instant.EPOCH));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(expiredRaw)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(tokens.refreshToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email)))
                .andExpect(status().isCreated());
    }

    private Tokens login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "correct-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return new Tokens(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    private static String registerJson(String username, String email) {
        String emailField = email == null ? "null" : "\"" + email + "\"";
        return "{\"username\":\"" + username + "\",\"password\":\"correct-password\","
                + "\"email\":" + emailField + ",\"displayName\":\"Test Player\"}";
    }

    private static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String refreshJson(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
