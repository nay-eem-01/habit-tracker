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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rest days (roadmap 9.3). The streak and stats rules themselves are in the calculator tests. */
@Transactional
class RestDayIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Test
    void aRestDayKeepsTheStreakAndCanBeTakenBack() throws Exception {
        String token = bearerFor("rest.streak@example.com");
        Integer habit = habitCreatedDaysAgo(token, "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}", 5);
        checkIn(token, habit, today.minusDays(3));
        checkIn(token, habit, today.minusDays(1));

        rest(token, habit, today.minusDays(2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.rest").value(true))
                .andExpect(jsonPath("$.payload.done").value(false));
        streak(token, habit).andExpect(jsonPath("$.payload.current").value(2));

        mockMvc.perform(delete("/api/habits/{id}/rest", habit).param("date", today.minusDays(2).toString())
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isNoContent());
        streak(token, habit).andExpect(jsonPath("$.payload.current").value(1));
    }

    @Test
    void oneRestDayAWeek() throws Exception {
        String token = bearerFor("rest.limit@example.com");
        Integer habit = habitCreatedDaysAgo(token, "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}", 7);
        // two days of the same Monday–Sunday week, neither in the future
        LocalDate first = today.getDayOfWeek() == DayOfWeek.MONDAY ? today.minusDays(1) : today;
        LocalDate second = first.minusDays(1);

        rest(token, habit, first).andExpect(status().isOk());
        rest(token, habit, first).andExpect(status().isOk());   // again: fine
        rest(token, habit, second).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REST_LIMIT_REACHED"));
    }

    @Test
    void notOnADoneDayAndACheckInEndsTheRest() throws Exception {
        String token = bearerFor("rest.done@example.com");
        Integer habit = habitCreatedDaysAgo(token, "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}", 3);
        checkIn(token, habit, today.minusDays(1));
        rest(token, habit, today.minusDays(1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REST_DAY_DONE"));

        rest(token, habit, today).andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.today.habits[0].resting").value(true))
                .andExpect(jsonPath("$.payload.today.habits[0].due").value(false));
        checkIn(token, habit, today);
        mockMvc.perform(get("/api/habits/{id}/logs", habit).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.content[0].rest").value(false))
                .andExpect(jsonPath("$.payload.content[0].done").value(true));
    }

    @Test
    void onlyDailyAndChosenWeekdayHabitsOnTheirDays() throws Exception {
        String token = bearerFor("rest.kinds@example.com");
        Integer weekly = habitCreatedDaysAgo(token,
                "{\"name\":\"Swim\",\"frequencyType\":\"X_TIMES_PER_WEEK\",\"frequencyConfig\":{\"timesPerWeek\":3}}", 0);
        rest(token, weekly, today).andExpect(jsonPath("$.errorCode").value("REST_NOT_ALLOWED"));
        Integer notToday = habitCreatedDaysAgo(token, "{\"name\":\"Gym\",\"frequencyType\":\"SPECIFIC_DAYS\","
                + "\"frequencyConfig\":{\"days\":[\"" + today.plusDays(1).getDayOfWeek() + "\"]}}", 0);
        rest(token, notToday, today).andExpect(jsonPath("$.errorCode").value("REST_NOT_ALLOWED"));
        Integer quit = habitCreatedDaysAgo(token, "{\"name\":\"No sugar\",\"kind\":\"QUIT\",\"frequencyType\":\"DAILY\"}", 0);
        rest(token, quit, today).andExpect(jsonPath("$.errorCode").value("REST_NOT_ALLOWED"));
    }

    private Integer habitCreatedDaysAgo(String token, String json, int daysAgo) throws Exception {
        String body = mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.payload.id");
        jdbcTemplate.update("update habits set created_at = created_at - make_interval(days => ?) where id = ?", daysAgo, id);
        entityManager.clear();
        return id;
    }

    private void checkIn(String token, Integer habit, LocalDate day) throws Exception {
        mockMvc.perform(post("/api/habits/{id}/checkin", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + day + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions rest(String token, Integer habit, LocalDate day) throws Exception {
        return mockMvc.perform(post("/api/habits/{id}/rest", habit).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + day + "\"}"));
    }

    private ResultActions streak(String token, Integer habit) throws Exception {
        return mockMvc.perform(get("/api/habits/{id}/streak", habit).header(HttpHeaders.AUTHORIZATION, token));
    }
}
