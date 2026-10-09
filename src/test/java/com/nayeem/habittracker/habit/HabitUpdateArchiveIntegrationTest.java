package com.nayeem.habittracker.habit;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class HabitUpdateArchiveIntegrationTest extends IntegrationTest {

    private static final String DAILY_READ = """
            {"name":"Read","frequencyType":"DAILY"}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updateReplacesEverything() throws Exception {
        String token = bearerFor("update@example.com");
        Integer id = createHabit(token, """
                {"name":"Read","category":"Learning","frequencyType":"DAILY","targetCount":2}""");

        mockMvc.perform(put("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Read more","frequencyType":"X_TIMES_PER_WEEK","frequencyConfig":{"timesPerWeek":4}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.name").value("Read more"))
                .andExpect(jsonPath("$.payload.category").doesNotExist())
                .andExpect(jsonPath("$.payload.frequencyType").value("X_TIMES_PER_WEEK"))
                .andExpect(jsonPath("$.payload.frequencyConfig.timesPerWeek").value(4))
                .andExpect(jsonPath("$.payload.targetCount").value(1));
    }

    @Test
    void updateValidatesTheScheduleToo() throws Exception {
        String token = bearerFor("update.bad@example.com");
        Integer id = createHabit(token, DAILY_READ);

        mockMvc.perform(put("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Read","frequencyType":"X_TIMES_PER_WEEK","frequencyConfig":{"timesPerWeek":7}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("HABIT_INVALID_FREQUENCY"));
    }

    @Test
    void archiveMovesItToTheArchivedListAndBack() throws Exception {
        String token = bearerFor("archive@example.com");
        Integer id = createHabit(token, DAILY_READ);

        mockMvc.perform(post("/api/habits/{id}/archive", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.archived").value(true));
        mockMvc.perform(post("/api/habits/{id}/archive", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        listCount(token, false, 0);
        listCount(token, true, 1);

        mockMvc.perform(post("/api/habits/{id}/unarchive", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.archived").value(false));
        listCount(token, false, 1);
        listCount(token, true, 0);
    }

    /** Every habit endpoint, with someone else's token: always 404, never the habit. */
    @Test
    void anotherUserCanNeitherSeeNorChangeIt() throws Exception {
        String alice = bearerFor("alice.upd@example.com");
        String bob = bearerFor("bob.upd@example.com");
        Integer id = createHabit(alice, DAILY_READ);

        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/habits/{id}", id),
                put("/api/habits/{id}", id).contentType(MediaType.APPLICATION_JSON).content(DAILY_READ),
                post("/api/habits/{id}/archive", id),
                post("/api/habits/{id}/unarchive", id));
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("HABIT_NOT_FOUND"));
        }

        // and Alice's habit is untouched
        mockMvc.perform(get("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(jsonPath("$.payload.name").value("Read"))
                .andExpect(jsonPath("$.payload.archived").value(false));
    }

    @Test
    void deletingAHabitTakesItsCheckInsWithIt() throws Exception {
        String token = bearerFor("habit.delete@example.com");
        Integer id = createHabit(token, DAILY_READ);
        mockMvc.perform(post("/api/habits/{id}/checkin", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        String other = bearerFor("habit.delete.other@example.com");
        mockMvc.perform(delete("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        listCount(token, false, 0);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from habit_logs where habit_id = ?", Long.class, id)).isZero();
    }

    private Integer createHabit(String token, String json) throws Exception {
        String body = mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }

    private void listCount(String token, boolean archived, int expected) throws Exception {
        mockMvc.perform(get("/api/habits").param("archived", String.valueOf(archived))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(expected));
    }
}
