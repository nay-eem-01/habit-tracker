package com.nayeem.habittracker.admin;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Other tests share the database, so the numbers are checked as "at least" or as a change. */
class AdminStatsIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void countsTodaysSignUpAndCheckInAndAFreeUserAtTheGoalLimit() throws Exception {
        String admin = bearerFor("admin.stats.admin@example.com");
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", "admin.stats.admin@example.com");
        String user = bearerFor("admin.stats.user@example.com");
        long atLimitBefore = atLimit(admin);

        Integer habit = JsonPath.read(send(user, "/api/habits", "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}"),
                "$.payload.id");
        send(user, "/api/habits/" + habit + "/checkin", "{}");
        send(user, "/api/goals", "{\"title\":\"One\"}");
        send(user, "/api/goals", "{\"title\":\"Two\"}");

        mockMvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.users.total").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.payload.users.signUps", hasSize(30)))
                .andExpect(jsonPath("$.payload.users.signUps[29].count").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.payload.active.day").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.payload.usage.activeHabits").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.payload.usage.checkIns", hasSize(30)))
                .andExpect(jsonPath("$.payload.delivery.since").value(notNullValue()));
        assertThat(atLimit(admin)).isEqualTo(atLimitBefore + 1);

        mockMvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isNotFound());
    }

    private long atLimit(String admin) throws Exception {
        String body = mockMvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.payload.usage.freeUsersAtLimit")).longValue();
    }

    private String send(String token, String path, String json) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }
}
