package com.ptit.poker.room.api;

import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
@SpringBootTest
@AutoConfigureMockMvc
class RoomSecurityMySqlIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void roomEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/rooms")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/rooms").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
