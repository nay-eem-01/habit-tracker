package com.nayeem.habittracker.security;

import com.nayeem.habittracker.common.logging.CorrelationIdFilter;
import com.nayeem.habittracker.support.IntegrationTest;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SecurityIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @Test
    void noTokenIs401InTheErrorShape() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void validTokenGetsThrough() throws Exception {
        User user = userService.createLocalUser("ping@example.com", "hash", "Ping", null);
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail());

        mockMvc.perform(get("/api/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.userId").value(user.getId()));
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mockMvc.perform(get("/api/ping").header(HttpHeaders.AUTHORIZATION, "Bearer nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForUnknownUserIs401() throws Exception {
        String token = jwtService.generateAccessToken(999L, "ghost@example.com");
        mockMvc.perform(get("/api/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void otherActuatorPathsAreInsideTheChain() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
