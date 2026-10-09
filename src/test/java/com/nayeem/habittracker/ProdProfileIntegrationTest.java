package com.nayeem.habittracker;

import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The production profile starts, and keeps the API docs and actuator details closed. */
@ActiveProfiles("prod")
@TestPropertySource(properties = {"CORS_ALLOWED_ORIGINS=https://app.example.com",
        "APP_FRONTEND_URL=https://app.example.com"})
class ProdProfileIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void noApiDocsAndOnlyHealth() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
