package com.ptit.poker.auth.api;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import com.ptit.poker.auth.infrastructure.persistence.UserEntity;
import com.ptit.poker.auth.infrastructure.persistence.UserRepository;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileEntity;
import com.ptit.poker.player.infrastructure.persistence.PlayerProfileRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

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
class RoleSeparationMySqlIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PlayerProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (long id : userIds) profiles.findByUserId(id).ifPresent(profiles::delete);
        profiles.flush();
        for (long id : userIds) jdbc.update("DELETE FROM users WHERE id=?", id);
    }

    @Test
    void playerCanReadPlayerApplicationApisAndSharedIdentity() throws Exception {
        UserEntity player = user(Role.PLAYER);
        String token = bearer(player);

        mvc.perform(get("/api/v1/rooms").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/rankings/leaderboard").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PLAYER"));
    }

    @Test
    void administratorCannotUsePlayerRestCapabilities() throws Exception {
        String token = bearer(user(Role.ADMIN));
        String gameId = UUID.randomUUID().toString();
        List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> requests = List.of(
                post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/rooms/1/join").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/rooms/1/leave"),
                post("/api/v1/games/rooms/1/start"),
                post("/api/v1/games/" + gameId + "/leave"),
                get("/api/v1/games/active/me"),
                get("/api/v1/rooms/1/chat/messages"),
                post("/api/v1/friend-requests").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/v1/rankings/leaderboard"),
                get("/api/v1/players/me/statistics"),
                get("/api/v1/analytics/me/summary"),
                patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content("{}"));

        for (var request : requests) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        }
    }

    @Test
    void administratorCanReadSharedCurrentIdentityWhilePlayerCannotReadAdminApi() throws Exception {
        UserEntity admin = user(Role.ADMIN);
        UserEntity player = user(Role.PLAYER);

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/v1/admin/overview").header(HttpHeaders.AUTHORIZATION, bearer(player)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/overview").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    private UserEntity user(Role role) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserEntity user = users.saveAndFlush(new UserEntity(
                role.name().toLowerCase() + "_" + suffix,
                "hash",
                suffix + "@example.test",
                role,
                AccountStatus.ACTIVE,
                5_000));
        userIds.add(user.getId());
        profiles.saveAndFlush(new PlayerProfileEntity(user.getId(), user.getUsername(), null, PresenceStatus.OFFLINE));
        return user;
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwt.createAccessToken(user);
    }
}
