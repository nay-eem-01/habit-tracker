package com.nayeem.habittracker.dashboard;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/dashboard} (roadmap A.1). The window arithmetic is in the calculator tests. */
@Transactional
class DashboardApiIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Test
    void todayAndCompletionForEveryActiveHabit() throws Exception {
        String token = bearerFor("dash@example.com");
        // 10 days old, done the last 7 days (today included)
        Integer read = habit(token, "{\"name\":\"Read\",\"frequencyType\":\"DAILY\"}", 10);
        for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
            checkIn(token, read, today.minusDays(daysAgo), 1);
        }
        // 3 of 8 glasses so far
        Integer water = habit(token, "{\"name\":\"water\",\"frequencyType\":\"DAILY\",\"targetCount\":8}", 0);
        checkIn(token, water, today, 3);
        habit(token, "{\"name\":\"Gym\",\"frequencyType\":\"X_TIMES_PER_WEEK\",\"frequencyConfig\":{\"timesPerWeek\":3}}", 0);
        // scheduled tomorrow's weekday only: not due today
        habit(token, "{\"name\":\"Yoga\",\"frequencyType\":\"SPECIFIC_DAYS\",\"frequencyConfig\":{\"days\":[\""
                + today.plusDays(1).getDayOfWeek() + "\"]}}", 0);
        Integer shelved = habit(token, "{\"name\":\"Archived\",\"frequencyType\":\"DAILY\"}", 0);
        mockMvc.perform(post("/api/habits/{id}/archive", shelved).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.today.date").value(today.toString()))
                .andExpect(jsonPath("$.payload.today.due").value(3))
                .andExpect(jsonPath("$.payload.today.done").value(1))
                // due first, by name (any case); then the rest; archived left out
                .andExpect(jsonPath("$.payload.today.habits.length()").value(4))
                .andExpect(jsonPath("$.payload.today.habits[*].name").value(
                        contains("Gym", "Read", "water", "Yoga")))
                .andExpect(jsonPath("$.payload.today.habits[0].due").value(true))
                .andExpect(jsonPath("$.payload.today.habits[0].doneThisWeek").value(0))
                .andExpect(jsonPath("$.payload.today.habits[0].timesPerWeek").value(3))
                .andExpect(jsonPath("$.payload.today.habits[0].streakUnit").value("WEEKS"))
                .andExpect(jsonPath("$.payload.today.habits[1].done").value(true))
                .andExpect(jsonPath("$.payload.today.habits[1].streak").value(7))
                .andExpect(jsonPath("$.payload.today.habits[1].doneThisWeek").doesNotExist())
                .andExpect(jsonPath("$.payload.today.habits[2].completedCount").value(3))
                .andExpect(jsonPath("$.payload.today.habits[2].targetCount").value(8))
                .andExpect(jsonPath("$.payload.today.habits[2].done").value(false))
                .andExpect(jsonPath("$.payload.today.habits[3].due").value(false))
                // only Read had anything expected: 7 of 7 now; before it, 4 days existed and none was done
                .andExpect(jsonPath("$.payload.completion.last7Days.rate").value(1.0))
                .andExpect(jsonPath("$.payload.completion.last7Days.previousRate").value(0.0))
                .andExpect(jsonPath("$.payload.completion.last7Days.change").value(1.0))
                .andExpect(jsonPath("$.payload.completion.last30Days.done").value(7))
                .andExpect(jsonPath("$.payload.completion.last30Days.previousRate").doesNotExist())
                .andExpect(jsonPath("$.payload.completion.habits.length()").value(4))
                .andExpect(jsonPath("$.payload.completion.habits[1].name").value("Read"))
                .andExpect(jsonPath("$.payload.completion.habits[1].last90Days.expected").value(11.0))
                .andExpect(jsonPath("$.payload.completion.habits[2].last7Days.rate").doesNotExist());
    }

    @Test
    void aNewUserHasAnEmptyDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearerFor("dash.new@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.today.due").value(0))
                .andExpect(jsonPath("$.payload.today.habits.length()").value(0))
                .andExpect(jsonPath("$.payload.completion.last7Days.rate").doesNotExist());
    }

    @Test
    void patternsFromRealCheckIns() throws Exception {
        String token = bearerFor("dash.patterns@example.com");
        Integer run = habit(token, "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}", 3);
        checkIn(token, run, today, 1);
        checkIn(token, run, today.minusDays(2), 1);   // logged today for 2 days ago: not a time-of-day signal
        Integer shelved = habit(token, "{\"name\":\"Old\",\"frequencyType\":\"DAILY\"}", 3);
        checkIn(token, shelved, today, 1);
        mockMvc.perform(post("/api/habits/{id}/archive", shelved).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard/patterns").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.heatmap.length()").value(365))
                .andExpect(jsonPath("$.payload.heatmap[364].date").value(today.toString()))
                .andExpect(jsonPath("$.payload.heatmap[364].done").value(1))
                .andExpect(jsonPath("$.payload.heatmap[364].expected").value(1))   // the archived habit left out
                .andExpect(jsonPath("$.payload.heatmap[363].ratio").value(0.0))
                .andExpect(jsonPath("$.payload.heatmap[362].ratio").value(1.0))
                .andExpect(jsonPath("$.payload.weekdays.length()").value(7))
                .andExpect(jsonPath("$.payload.weekdays[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.payload.hours.length()").value(24))
                .andExpect(jsonPath("$.payload.hours[*]").value(hasItem(1)))
                .andExpect(jsonPath("$.payload.peakHour").isNumber());
        String hours = mockMvc.perform(get("/api/dashboard/patterns").header(HttpHeaders.AUTHORIZATION, token))
                .andReturn().getResponse().getContentAsString();
        List<Integer> perHour = JsonPath.read(hours, "$.payload.hours");
        assertThat(perHour.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);   // only today's same-day check-in
    }

    @Test
    void needsAToken() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    private Integer habit(String token, String json, int createdDaysAgo) throws Exception {
        String body = mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.payload.id");
        jdbcTemplate.update("update habits set created_at = created_at - make_interval(days => ?) where id = ?",
                createdDaysAgo, id);
        entityManager.clear();   // drop the cached habit so the backdated created_at is read
        return id;
    }

    private void checkIn(String token, Integer habitId, LocalDate date, int count) throws Exception {
        mockMvc.perform(post("/api/habits/{id}/checkin", habitId).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"completedCount\":" + count + "}"))
                .andExpect(status().isOk());
    }
}
