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

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CheckInIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    // bearerFor() users are in UTC
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Test
    void emptyBodyMarksTodayDoneAndRepeatingKeepsOneRow() throws Exception {
        String token = bearerFor("checkin@example.com");
        Integer habitId = habit(token, 1);

        String first = checkIn(token, habitId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.date").value(today.toString()))
                .andExpect(jsonPath("$.payload.completedCount").value(1))
                .andExpect(jsonPath("$.payload.done").value(true))
                .andReturn().getResponse().getContentAsString();
        String again = checkIn(token, habitId, "{}").andReturn().getResponse().getContentAsString();

        assertThat((Integer) JsonPath.read(again, "$.payload.id")).isEqualTo(JsonPath.read(first, "$.payload.id"));
        assertThat(rows(habitId)).isEqualTo(1);
    }

    @Test
    void countIsAbsoluteAndZeroUndoes() throws Exception {
        String token = bearerFor("water@example.com");
        Integer habitId = habit(token, 8);

        checkIn(token, habitId, "{\"completedCount\":5}")
                .andExpect(jsonPath("$.payload.completedCount").value(5))
                .andExpect(jsonPath("$.payload.done").value(false));
        checkIn(token, habitId, "{\"completedCount\":8}")
                .andExpect(jsonPath("$.payload.done").value(true));
        checkIn(token, habitId, "{\"completedCount\":0}")
                .andExpect(jsonPath("$.payload.completedCount").value(0))
                .andExpect(jsonPath("$.payload.done").value(false));
        assertThat(rows(habitId)).isEqualTo(1);
    }

    @Test
    void noteIsKeptWhenOmittedAndClearedWhenEmpty() throws Exception {
        String token = bearerFor("notes@example.com");
        Integer habitId = habit(token, 1);

        checkIn(token, habitId, "{\"note\":\"felt great\"}").andExpect(jsonPath("$.payload.note").value("felt great"));
        checkIn(token, habitId, "{\"completedCount\":1}").andExpect(jsonPath("$.payload.note").value("felt great"));
        checkIn(token, habitId, "{\"note\":\"\"}").andExpect(jsonPath("$.payload.note").doesNotExist());
    }

    @Test
    void dateRules() throws Exception {
        String token = bearerFor("dates@example.com");
        Integer habitId = habit(token, 1);

        checkIn(token, habitId, dateBody(today.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("LOG_DATE_OUT_OF_RANGE"));
        // created just now, so yesterday is before the habit existed
        checkIn(token, habitId, dateBody(today.minusDays(1))).andExpect(status().isBadRequest());

        jdbcTemplate.update("update habits set created_at = created_at - interval '30 days' where id = ?", habitId);
        entityManager.clear(); // drop the cached habit so the backdated created_at is read
        checkIn(token, habitId, dateBody(today.minusDays(7))).andExpect(status().isOk());
        checkIn(token, habitId, dateBody(today.minusDays(8))).andExpect(status().isBadRequest());
    }

    @Test
    void archivedHabitCantBeCheckedIn() throws Exception {
        String token = bearerFor("archived.ci@example.com");
        Integer habitId = habit(token, 1);
        mockMvc.perform(post("/api/habits/{id}/archive", habitId).header(HttpHeaders.AUTHORIZATION, token));

        checkIn(token, habitId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HABIT_ARCHIVED"));
    }

    @Test
    void someoneElsesHabitIsNotFound() throws Exception {
        Integer habitId = habit(bearerFor("owner.ci@example.com"), 1);
        checkIn(bearerFor("other.ci@example.com"), habitId, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HABIT_NOT_FOUND"));
        assertThat(rows(habitId)).isZero();
    }

    private Integer habit(String token, int targetCount) throws Exception {
        String body = mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Habit","frequencyType":"DAILY","targetCount":%d}""".formatted(targetCount)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }

    private ResultActions checkIn(String token, Integer habitId, String json) throws Exception {
        var request = post("/api/habits/{id}/checkin", habitId).header(HttpHeaders.AUTHORIZATION, token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private int rows(Integer habitId) {
        return jdbcTemplate.queryForObject("select count(*) from habit_logs where habit_id = ?", Integer.class, habitId);
    }

    private static String dateBody(LocalDate date) {
        return "{\"date\":\"" + date + "\"}";
    }
}
