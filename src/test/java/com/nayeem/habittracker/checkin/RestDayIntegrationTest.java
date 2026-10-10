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
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

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

    @Test
    void theWeeksFirstRestIsFreeThenTheyCostXpAndTakingOneBackRefundsIt() throws Exception {
        String token = bearerFor("rest.xp@example.com");
        achieveAGoal(token);   // +500 XP
        Integer habit = habitCreatedDaysAgo(token, "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}", 8);
        LocalDate first = firstOfFourDaysInOneWeek();

        rest(token, habit, first).andExpect(jsonPath("$.payload.restCostXp").value(0));
        rest(token, habit, first.plusDays(1)).andExpect(jsonPath("$.payload.restCostXp").value(100));
        rest(token, habit, first.plusDays(2)).andExpect(jsonPath("$.payload.restCostXp").value(200));
        rest(token, habit, first.plusDays(3)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REST_LIMIT_REACHED"));
        level(token)
                .andExpect(jsonPath("$.payload.xp").value(500))   // the level never drops for it
                .andExpect(jsonPath("$.payload.spentXp").value(300))
                .andExpect(jsonPath("$.payload.xpBalance").value(200));

        mockMvc.perform(delete("/api/habits/{id}/rest", habit).param("date", first.plusDays(2).toString())
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isNoContent());
        level(token).andExpect(jsonPath("$.payload.xpBalance").value(400));
    }

    @Test
    void aPaidRestNeedsTheXp() throws Exception {
        String token = bearerFor("rest.poor@example.com");
        Integer habit = habitCreatedDaysAgo(token, "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}", 8);
        LocalDate first = firstOfFourDaysInOneWeek();

        rest(token, habit, first).andExpect(status().isOk());
        rest(token, habit, first.plusDays(1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("XP_NOT_ENOUGH"));
    }

    /** Four days of one Monday–Sunday week, all within the 7 days back a rest may be set. */
    private LocalDate firstOfFourDaysInOneWeek() {
        LocalDate earliest = today.minusDays(7);
        LocalDate itsSunday = earliest.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        long leftInItsWeek = ChronoUnit.DAYS.between(earliest, itsSunday) + 1;
        return leftInItsWeek >= 4 ? earliest : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void achieveAGoal(String token) throws Exception {
        String body = mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"5K\"}"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/goals/{id}/achieve", JsonPath.<Integer>read(body, "$.payload.id"))
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
    }

    private ResultActions level(String token) throws Exception {
        return mockMvc.perform(get("/api/me/level").header(HttpHeaders.AUTHORIZATION, token));
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
