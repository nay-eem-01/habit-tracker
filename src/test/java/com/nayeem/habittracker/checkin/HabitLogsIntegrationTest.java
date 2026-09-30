package com.nayeem.habittracker.checkin;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class HabitLogsIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Test
    void newestFirstWithinTheRange() throws Exception {
        String token = bearerFor("logs@example.com");
        Integer habitId = backdatedHabit(token);
        for (int daysAgo : new int[]{0, 1, 3, 6}) {
            checkIn(token, habitId, today.minusDays(daysAgo));
        }

        mockMvc.perform(get("/api/habits/{id}/logs", habitId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.totalElements").value(4))
                .andExpect(jsonPath("$.payload.content[0].date").value(today.toString()))
                .andExpect(jsonPath("$.payload.content[3].date").value(today.minusDays(6).toString()));

        mockMvc.perform(get("/api/habits/{id}/logs", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .param("from", today.minusDays(3).toString()).param("to", today.minusDays(1).toString()))
                .andExpect(jsonPath("$.payload.totalElements").value(2));
    }

    @Test
    void badRangeIs400() throws Exception {
        String token = bearerFor("logs.range@example.com");
        Integer habitId = backdatedHabit(token);

        mockMvc.perform(get("/api/habits/{id}/logs", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .param("from", today.toString()).param("to", today.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/habits/{id}/logs", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .param("from", today.minusDays(366).toString()).param("to", today.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/habits/{id}/logs", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .param("from", today.minusDays(365).toString()).param("to", today.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void someoneElsesLogsAreNotFound() throws Exception {
        Integer habitId = backdatedHabit(bearerFor("logs.owner@example.com"));
        mockMvc.perform(get("/api/habits/{id}/logs", habitId)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("logs.other@example.com")))
                .andExpect(status().isNotFound());
    }

    private Integer backdatedHabit(String token) throws Exception {
        String body = mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Read\",\"frequencyType\":\"DAILY\"}"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.payload.id");
        jdbcTemplate.update("update habits set created_at = created_at - interval '30 days' where id = ?", id);
        entityManager.clear();
        return id;
    }

    private void checkIn(String token, Integer habitId, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/habits/{id}/checkin", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + date + "\"}"))
                .andExpect(status().isOk());
    }
}
