package com.nayeem.habittracker.habit;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class HabitGoalLinkIntegrationTest extends IntegrationTest {

    private static final String DAILY_READ = """
            {"name":"Read","frequencyType":"DAILY"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void linkSetsTheGoalAndTargetAndUnlinkClearsThem() throws Exception {
        String token = bearerFor("link@example.com");
        Integer goal = createGoal(token);
        Integer habit = createHabit(token);

        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(link(goal, 60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.goalId").value(goal))
                .andExpect(jsonPath("$.payload.goalTargetDays").value(60));

        mockMvc.perform(delete("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.goalId").doesNotExist())
                .andExpect(jsonPath("$.payload.goalTargetDays").doesNotExist());
        mockMvc.perform(delete("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        assertThat(habitRepository.findById(habit.longValue()).orElseThrow().getGoalLinkedOn()).isNull();
    }

    @Test
    void linkingAgainChangesTheTargetAndKeepsTheStartDay() throws Exception {
        String token = bearerFor("relink@example.com");
        Integer goal = createGoal(token);
        Integer habit = createHabit(token);
        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(link(goal, 60)));
        Habit stored = habitRepository.findById(habit.longValue()).orElseThrow();
        LocalDate linkedOn = stored.getGoalLinkedOn();
        assertThat(linkedOn).isNotNull();
        entityManager.createNativeQuery("update habits set goal_linked_on = :d where id = :id")
                .setParameter("d", linkedOn.minusDays(10)).setParameter("id", habit).executeUpdate();
        entityManager.clear();

        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(link(goal, 90)))
                .andExpect(jsonPath("$.payload.goalTargetDays").value(90));

        assertThat(habitRepository.findById(habit.longValue()).orElseThrow().getGoalLinkedOn())
                .isEqualTo(linkedOn.minusDays(10));
    }

    @Test
    void movingToAnotherGoalRestartsTheCount() throws Exception {
        String token = bearerFor("move@example.com");
        Integer first = createGoal(token);
        Integer second = createGoal(token);
        Integer habit = createHabit(token);
        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(link(first, 30)));
        entityManager.createNativeQuery("update habits set goal_linked_on = date '2020-01-01' where id = :id")
                .setParameter("id", habit).executeUpdate();
        entityManager.clear();

        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(link(second, 30)))
                .andExpect(jsonPath("$.payload.goalId").value(second));

        assertThat(habitRepository.findById(habit.longValue()).orElseThrow().getGoalLinkedOn())
                .isAfter(LocalDate.of(2020, 1, 1));
    }

    @Test
    void rejectsBadTargetsAndMissingFields() throws Exception {
        String token = bearerFor("link.invalid@example.com");
        Integer goal = createGoal(token);
        Integer habit = createHabit(token);

        for (String body : List.of(link(goal, 0), link(goal, 3651), "{\"goalTargetDays\":5}",
                "{\"goalId\":" + goal + "}")) {
            mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void onlyAnActiveGoalAndAnActiveHabitCanBeLinked() throws Exception {
        String token = bearerFor("link.state@example.com");
        Integer goal = createGoal(token);
        Integer habit = createHabit(token);

        entityManager.createNativeQuery("update goals set status = 'ABANDONED' where id = :id")
                .setParameter("id", goal).executeUpdate();
        entityManager.clear();
        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(link(goal, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_ACTIVE"));

        Integer active = createGoal(token);
        mockMvc.perform(post("/api/habits/{id}/archive", habit).header(HttpHeaders.AUTHORIZATION, token));
        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(link(active, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HABIT_ARCHIVED"));
    }

    @Test
    void replacingOrArchivingTheHabitKeepsItsGoal() throws Exception {
        String token = bearerFor("link.keep@example.com");
        Integer goal = createGoal(token);
        Integer habit = createHabit(token);
        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(link(goal, 60)));

        mockMvc.perform(put("/api/habits/{id}", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Read more","frequencyType":"DAILY"}"""))
                .andExpect(jsonPath("$.payload.goalId").value(goal))
                .andExpect(jsonPath("$.payload.goalTargetDays").value(60));
        mockMvc.perform(post("/api/habits/{id}/archive", habit).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.goalId").value(goal));
    }

    @Test
    void anotherUsersHabitOrGoalIsNotFound() throws Exception {
        String alice = bearerFor("alice.link@example.com");
        String bob = bearerFor("bob.link@example.com");
        Integer aliceGoal = createGoal(alice);
        Integer aliceHabit = createHabit(alice);
        Integer bobHabit = createHabit(bob);

        List<MockHttpServletRequestBuilder> asBob = List.of(
                put("/api/habits/{id}/goal", aliceHabit).contentType(MediaType.APPLICATION_JSON).content(link(aliceGoal, 5)),
                delete("/api/habits/{id}/goal", aliceHabit));
        for (MockHttpServletRequestBuilder request : asBob) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("HABIT_NOT_FOUND"));
        }
        mockMvc.perform(put("/api/habits/{id}/goal", bobHabit).header(HttpHeaders.AUTHORIZATION, bob)
                        .contentType(MediaType.APPLICATION_JSON).content(link(aliceGoal, 5)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_FOUND"));
    }

    private static String link(Integer goalId, int targetDays) {
        return "{\"goalId\":" + goalId + ",\"goalTargetDays\":" + targetDays + "}";
    }

    private Integer createGoal(String token) throws Exception {
        return create("/api/goals", token, """
                {"title":"Read more"}""");
    }

    private Integer createHabit(String token) throws Exception {
        return create("/api/habits", token, DAILY_READ);
    }

    private Integer create(String url, String token, String json) throws Exception {
        String body = mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }
}
