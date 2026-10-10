package com.nayeem.habittracker.user;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Free plan: 7 active habits, 2 active goals (roadmap 9.4). */
@Transactional
class PlanLimitsIntegrationTest extends IntegrationTest {

    private static final String HABIT = "{\"name\":\"Read\",\"frequencyType\":\"DAILY\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void sevenActiveHabitsArchivedOnesDontCount() throws Exception {
        String token = bearerFor("plan.habits@example.com");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.plan").value("FREE"));
        Integer first = null;
        for (int i = 0; i < 7; i++) {
            Integer id = JsonPath.read(send(token, "/api/habits", HABIT).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "$.payload.id");
            first = first == null ? id : first;
        }
        send(token, "/api/habits", HABIT).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PLAN_LIMIT_REACHED"));

        send(token, "/api/habits/" + first + "/archive", "").andExpect(status().isOk());
        send(token, "/api/habits", HABIT).andExpect(status().isCreated());
        send(token, "/api/habits/" + first + "/unarchive", "").andExpect(status().isForbidden());
    }

    @Test
    void twoActiveGoalsAndProHasNoLimit() throws Exception {
        String token = bearerFor("plan.goals@example.com");
        send(token, "/api/goals", "{\"title\":\"One\"}").andExpect(status().isCreated());
        send(token, "/api/goals", "{\"title\":\"Two\"}").andExpect(status().isCreated());
        send(token, "/api/goals", "{\"title\":\"Three\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PLAN_LIMIT_REACHED"));

        jdbcTemplate.update("update users set plan = 'PRO' where email = ?", "plan.goals@example.com");
        entityManager.clear();   // drop the cached FREE user
        send(token, "/api/goals", "{\"title\":\"Three\"}").andExpect(status().isCreated());
    }

    private ResultActions send(String token, String path, String json) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
