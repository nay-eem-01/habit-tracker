package com.nayeem.habittracker.security;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RolesAndSuspensionIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aUserIsAUserAndTheAdminApiIsNotFoundForThem() throws Exception {
        String token = register("roles.user@example.com");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.role").value("USER"));
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void anAdminOnlyAdministersAndNeverUsesTheAppItself() throws Exception {
        String token = register("roles.admin@example.com");
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", "roles.admin@example.com");

        mockMvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.role").value("ADMIN"));
        for (String path : new String[]{"/api/habits", "/api/dashboard", "/api/goals", "/api/notifications",
                "/api/me/export"}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }
        mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSuspendedUserLosesAccessAtOnceAndCannotSignInAgain() throws Exception {
        String token = register("roles.suspended@example.com");
        jdbcTemplate.update("update users set suspended_at = now() where email = ?", "roles.suspended@example.com");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        login("roles.suspended@example.com", "password123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_ACCOUNT_SUSPENDED"));
        // without the right password nothing tells that the account exists, let alone that it's suspended
        login("roles.suspended@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_CREDENTIALS"));

        jdbcTemplate.update("update users set suspended_at = null where email = ?", "roles.suspended@example.com");
        login("roles.suspended@example.com", "password123").andExpect(status().isOk());
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"password123","name":"Test"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.payload.accessToken");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)));
    }
}
