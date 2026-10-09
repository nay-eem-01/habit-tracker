package com.nayeem.habittracker.habit;

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

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quit habits (roadmap 9.2): check-ins are slips, clean days count. */
@Transactional
class QuitHabitIntegrationTest extends IntegrationTest {

    private static final String QUIT = "{\"name\":\"No sugar\",\"kind\":\"QUIT\",\"frequencyType\":\"DAILY\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Test
    void cleanDaysCountTodayIncludedAndASlipBreaksTheRunAtOnce() throws Exception {
        String token = bearerFor("quit.streak@example.com");
        Integer habit = quitHabitCreatedDaysAgo(token, 5);
        streak(token, habit).andExpect(jsonPath("$.payload.current").value(6));

        slip(token, habit, today.minusDays(2));
        streak(token, habit)
                .andExpect(jsonPath("$.payload.current").value(2))
                .andExpect(jsonPath("$.payload.longest").value(3));

        slip(token, habit, today);
        streak(token, habit).andExpect(jsonPath("$.payload.current").value(0));
        // clean: 5, 4 and 3 days ago, and yesterday — 4 × 10, no 7-day run so no bonus
        mockMvc.perform(get("/api/me/level").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.xp").value(40));
    }

    @Test
    void aQuitHabitIsNeverDueButShowsOnToday() throws Exception {
        String token = bearerFor("quit.today@example.com");
        quitHabitCreatedDaysAgo(token, 0);
        mockMvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.today.due").value(0))
                .andExpect(jsonPath("$.payload.today.habits[0].kind").value("QUIT"))
                .andExpect(jsonPath("$.payload.today.habits[0].due").value(false))
                .andExpect(jsonPath("$.payload.today.habits[0].done").value(true));
    }

    @Test
    void quitRules() throws Exception {
        String token = bearerFor("quit.rules@example.com");
        create(token, "{\"name\":\"x\",\"kind\":\"QUIT\",\"frequencyType\":\"X_TIMES_PER_WEEK\",\"frequencyConfig\":{\"timesPerWeek\":3}}")
                .andExpect(jsonPath("$.errorCode").value("HABIT_QUIT_INVALID"));
        create(token, "{\"name\":\"x\",\"kind\":\"QUIT\",\"frequencyType\":\"DAILY\",\"reminderTime\":\"07:30\"}")
                .andExpect(jsonPath("$.errorCode").value("HABIT_QUIT_INVALID"));
        create(token, "{\"name\":\"x\",\"kind\":\"QUIT\",\"frequencyType\":\"DAILY\",\"targetCount\":2}")
                .andExpect(jsonPath("$.errorCode").value("HABIT_QUIT_INVALID"));

        Integer habit = id(create(token, QUIT).andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.kind").value("QUIT")));
        mockMvc.perform(put("/api/habits/{id}", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"kind\":\"BUILD\",\"frequencyType\":\"DAILY\"}"))
                .andExpect(jsonPath("$.errorCode").value("HABIT_QUIT_INVALID"));
        Integer goal = id(mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Health\"}")));
        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\":" + goal + ",\"goalTargetDays\":30}"))
                .andExpect(jsonPath("$.errorCode").value("HABIT_QUIT_INVALID"));
    }

    private Integer quitHabitCreatedDaysAgo(String token, int daysAgo) throws Exception {
        Integer id = id(create(token, QUIT).andExpect(status().isCreated()));
        jdbcTemplate.update("update habits set created_at = created_at - make_interval(days => ?) where id = ?", daysAgo, id);
        entityManager.clear();
        return id;
    }

    private ResultActions create(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void slip(String token, Integer habit, LocalDate day) throws Exception {
        mockMvc.perform(post("/api/habits/{id}/checkin", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + day + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions streak(String token, Integer habit) throws Exception {
        return mockMvc.perform(get("/api/habits/{id}/streak", habit).header(HttpHeaders.AUTHORIZATION, token));
    }

    private static Integer id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.payload.id");
    }
}
