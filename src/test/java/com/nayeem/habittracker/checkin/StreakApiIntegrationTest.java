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
class StreakApiIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Test
    void streakFromRealCheckIns() throws Exception {
        String token = bearerFor("streak@example.com");
        Integer habitId = dailyHabitCreatedDaysAgo(token, 10, 2);

        // 4 and 3 days ago done, 2 days ago missed, yesterday and today done
        for (int daysAgo : new int[]{4, 3, 1, 0}) {
            checkIn(token, habitId, today.minusDays(daysAgo), 2);
        }
        // counted but below target: not a done day
        checkIn(token, habitId, today.minusDays(2), 1);

        mockMvc.perform(get("/api/habits/{id}/streak", habitId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.current").value(2))
                .andExpect(jsonPath("$.payload.longest").value(2))
                .andExpect(jsonPath("$.payload.unit").value("DAYS"));
    }

    @Test
    void someoneElsesStreakIsNotFound() throws Exception {
        Integer habitId = dailyHabitCreatedDaysAgo(bearerFor("streak.owner@example.com"), 0, 1);
        mockMvc.perform(get("/api/habits/{id}/streak", habitId)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("streak.other@example.com")))
                .andExpect(status().isNotFound());
    }

    private Integer dailyHabitCreatedDaysAgo(String token, int daysAgo, int targetCount) throws Exception {
        String body = mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Run\",\"frequencyType\":\"DAILY\",\"targetCount\":" + targetCount + "}"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.payload.id");
        jdbcTemplate.update("update habits set created_at = created_at - make_interval(days => ?) where id = ?", daysAgo, id);
        entityManager.clear();
        return id;
    }

    private void checkIn(String token, Integer habitId, LocalDate date, int count) throws Exception {
        mockMvc.perform(post("/api/habits/{id}/checkin", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"completedCount\":" + count + "}"))
                .andExpect(status().isOk());
    }
}
