package com.nayeem.habittracker.level;

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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/me/level} (roadmap X.2). The arithmetic itself is in {@code XpCalculatorTest}. */
@Transactional
class LevelApiIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Test
    void aNewUserIsLevelOne() throws Exception {
        mockMvc.perform(get("/api/me/level").header(HttpHeaders.AUTHORIZATION, bearerFor("level.new@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.xp").value(0))
                .andExpect(jsonPath("$.payload.level").value(1))
                .andExpect(jsonPath("$.payload.tier").value("BRONZE"))
                .andExpect(jsonPath("$.payload.xpForNextLevel").value(100))
                .andExpect(jsonPath("$.payload.progressToNextLevel").value(0.0));
    }

    @Test
    void addsUpEveryHabitArchivedOnesTooAndAchievedGoals() throws Exception {
        String token = bearerFor("level.sum@example.com");

        // 10 days old; done the last 7 days in a row and today: 6 × 10 + (10 + 5 + 50) + 15 = 140
        Integer running = dailyHabitCreatedDaysAgo(token, 10);
        for (int daysAgo = 7; daysAgo >= 0; daysAgo--) {
            checkIn(token, running, today.minusDays(daysAgo));
        }
        // done today, then archived: its 10 stay
        Integer shelved = dailyHabitCreatedDaysAgo(token, 0);
        checkIn(token, shelved, today);
        mockMvc.perform(post("/api/habits/{id}/archive", shelved).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        // achieved: +500; abandoned: nothing
        mockMvc.perform(post("/api/goals/{id}/achieve", createGoal(token)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/goals/{id}/abandon", createGoal(token)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        // someone else's check-ins don't count
        String other = bearerFor("level.other@example.com");
        checkIn(other, dailyHabitCreatedDaysAgo(other, 0), today);

        // 140 + 10 + 500 = 650: level 4 runs from 600 to 1000
        mockMvc.perform(get("/api/me/level").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.xp").value(650))
                .andExpect(jsonPath("$.payload.level").value(4))
                .andExpect(jsonPath("$.payload.tier").value("BRONZE"))
                .andExpect(jsonPath("$.payload.xpForNextLevel").value(1000))
                .andExpect(jsonPath("$.payload.progressToNextLevel").value(0.12));
        mockMvc.perform(get("/api/me/level").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(jsonPath("$.payload.xp").value(10));
    }

    @Test
    void aNewScheduleStartsANewStreakAndKeepsTheXp() throws Exception {
        String token = bearerFor("level.reschedule@example.com");
        Integer habit = dailyHabitCreatedDaysAgo(token, 10);
        for (int daysAgo = 3; daysAgo >= 1; daysAgo--) {
            checkIn(token, habit, today.minusDays(daysAgo));
        }
        putSchedule(token, habit, "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}");   // unchanged: no new streak
        mockMvc.perform(get("/api/habits/{id}/streak", habit).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.current").value(3));

        // every weekday but today's: past days would now be judged by it — they aren't
        String days = Arrays.stream(DayOfWeek.values())
                .filter(d -> d != today.getDayOfWeek()).map(d -> "\"" + d + "\"")
                .collect(Collectors.joining(","));
        putSchedule(token, habit, "{\"name\":\"Run\",\"frequencyType\":\"SPECIFIC_DAYS\",\"frequencyConfig\":{\"days\":["
                + days + "]}}");

        mockMvc.perform(get("/api/habits/{id}/streak", habit).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.current").value(0))
                .andExpect(jsonPath("$.payload.longest").value(0));
        mockMvc.perform(get("/api/me/level").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.xp").value(30));
    }

    @Test
    void needsAToken() throws Exception {
        mockMvc.perform(get("/api/me/level")).andExpect(status().isUnauthorized());
    }

    private Integer dailyHabitCreatedDaysAgo(String token, int daysAgo) throws Exception {
        String body = mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.payload.id");
        jdbcTemplate.update("update habits set created_at = created_at - make_interval(days => ?) where id = ?", daysAgo, id);
        entityManager.clear();   // drop the cached habit so the backdated created_at is read
        return id;
    }

    private void putSchedule(String token, Integer habitId, String json) throws Exception {
        mockMvc.perform(put("/api/habits/{id}", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
        entityManager.clear();
    }

    private void checkIn(String token, Integer habitId, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/habits/{id}/checkin", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + date + "\"}"))
                .andExpect(status().isOk());
    }

    private Integer createGoal(String token) throws Exception {
        String body = mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Run a 5K\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }
}
