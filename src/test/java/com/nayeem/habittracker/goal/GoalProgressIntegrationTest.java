package com.nayeem.habittracker.goal;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GoalProgressIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aGoalWithNoHabitsIsAtZero() throws Exception {
        String token = bearerFor("progress.empty@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Read\"}");

        mockMvc.perform(get("/api/goals/{id}/progress", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.percent").value(0))
                .andExpect(jsonPath("$.payload.habits.length()").value(0));
    }

    @Test
    void countsOnlyDoneDaysFromTheLinkOnAndAveragesTheHabits() throws Exception {
        String token = bearerFor("progress.math@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Get fit\"}");
        Integer run = create("/api/habits", token, "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}");
        Integer stretch = create("/api/habits", token, "{\"name\":\"Stretch\",\"frequencyType\":\"DAILY\"}");
        link(token, run, goal, 4);
        link(token, stretch, goal, 10);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // Run was linked 3 days ago; done 2 days ago, yesterday and today — and once before it was linked.
        entityManager.createNativeQuery("update habits set goal_linked_on = :d where id = :id")
                .setParameter("d", today.minusDays(3)).setParameter("id", run).executeUpdate();
        for (int back : new int[]{5, 2, 1}) {
            insertLog(run, today.minusDays(back), 1);
        }
        checkIn(token, run);
        // Stretch: today done only.
        checkIn(token, stretch);
        entityManager.clear();

        mockMvc.perform(get("/api/goals/{id}/progress", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.habits[0].name").value("Run"))
                .andExpect(jsonPath("$.payload.habits[0].doneDays").value(3))
                .andExpect(jsonPath("$.payload.habits[0].percent").value(75))
                .andExpect(jsonPath("$.payload.habits[1].doneDays").value(1))
                .andExpect(jsonPath("$.payload.habits[1].percent").value(10))
                .andExpect(jsonPath("$.payload.percent").value(43));   // (0.75 + 0.10) / 2 = 42.5 -> 43
    }

    @Test
    void aDayBelowTheDailyTargetIsNotDone() throws Exception {
        String token = bearerFor("progress.target@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Hydrate\"}");
        Integer water = create("/api/habits", token,
                "{\"name\":\"Water\",\"frequencyType\":\"DAILY\",\"targetCount\":3}");
        link(token, water, goal, 2);
        mockMvc.perform(post("/api/habits/{id}/checkin", water).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"completedCount\":2}"));

        mockMvc.perform(get("/api/goals/{id}/progress", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.habits[0].doneDays").value(0))
                .andExpect(jsonPath("$.payload.percent").value(0));
    }

    @Test
    void progressIsCappedAndAnArchivedHabitIsListedButNotCounted() throws Exception {
        String token = bearerFor("progress.archived@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Write\"}");
        Integer done = create("/api/habits", token, "{\"name\":\"Draft\",\"frequencyType\":\"DAILY\"}");
        Integer shelved = create("/api/habits", token, "{\"name\":\"Edit\",\"frequencyType\":\"DAILY\"}");
        link(token, done, goal, 1);
        link(token, shelved, goal, 50);
        checkIn(token, done);
        mockMvc.perform(post("/api/habits/{id}/archive", shelved).header(HttpHeaders.AUTHORIZATION, token));

        mockMvc.perform(get("/api/goals/{id}/progress", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.habits.length()").value(2))
                .andExpect(jsonPath("$.payload.habits[1].archived").value(true))
                .andExpect(jsonPath("$.payload.percent").value(100));
    }

    @Test
    void anotherUsersGoalIsNotFound() throws Exception {
        String alice = bearerFor("alice.progress@example.com");
        String bob = bearerFor("bob.progress@example.com");
        Integer goal = create("/api/goals", alice, "{\"title\":\"Mine\"}");

        mockMvc.perform(get("/api/goals/{id}/progress", goal).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_FOUND"));
    }

    private void link(String token, Integer habit, Integer goal, int days) throws Exception {
        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\":" + goal + ",\"goalTargetDays\":" + days + "}"))
                .andExpect(status().isOk());
    }

    private void checkIn(String token, Integer habit) throws Exception {
        mockMvc.perform(post("/api/habits/{id}/checkin", habit).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    /** A back-dated log: the API refuses days before the habit existed, so tests write it directly. */
    private void insertLog(Integer habit, LocalDate day, int count) {
        entityManager.createNativeQuery("""
                        insert into habit_logs (habit_id, log_date, completed_count, target_count,
                                                created_at, created_by, last_modified_at, last_modified_by)
                        select :h, :d, :c, target_count, now(), 'SYSTEM', now(), 'SYSTEM' from habits where id = :h""")
                .setParameter("h", habit).setParameter("d", day).setParameter("c", count).executeUpdate();
    }

    private Integer create(String url, String token, String json) throws Exception {
        String body = mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }
}
