package com.nayeem.habittracker.goal;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GoalStatusIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void achieveMarksItAndStampsTheTime() throws Exception {
        String token = bearerFor("achieve@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Read 12 books\"}");

        mockMvc.perform(post("/api/goals/{id}/achieve", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.status").value("ACHIEVED"))
                .andExpect(jsonPath("$.payload.achievedAt").exists());
        mockMvc.perform(get("/api/goals").param("status", "ACHIEVED").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(1));
    }

    @Test
    void repeatingTheSameCallKeepsTheFirstTime() throws Exception {
        String token = bearerFor("achieve.twice@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Read\"}");
        String first = mockMvc.perform(post("/api/goals/{id}/achieve", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/goals/{id}/achieve", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(second, "$.payload.achievedAt"))
                .isEqualTo(JsonPath.read(first, "$.payload.achievedAt"));
        mockMvc.perform(post("/api/goals/{id}/abandon", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GOAL_ALREADY_CLOSED"));
    }

    @Test
    void abandonKeepsTheHabitsLinkedAndHasNoAchievedTime() throws Exception {
        String token = bearerFor("abandon@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Learn Spanish\"}");
        Integer habit = create("/api/habits", token, "{\"name\":\"Duolingo\",\"frequencyType\":\"DAILY\"}");
        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\":" + goal + ",\"goalTargetDays\":30}"));

        mockMvc.perform(post("/api/goals/{id}/abandon", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.status").value("ABANDONED"))
                .andExpect(jsonPath("$.payload.achievedAt").doesNotExist());
        mockMvc.perform(post("/api/goals/{id}/abandon", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/habits/{id}", habit).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.goalId").value(goal));
        mockMvc.perform(post("/api/goals/{id}/achieve", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GOAL_ALREADY_CLOSED"));
    }

    @Test
    void aClosedGoalAcceptsNoNewHabitLinks() throws Exception {
        String token = bearerFor("closed.link@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Done\"}");
        Integer habit = create("/api/habits", token, "{\"name\":\"Read\",\"frequencyType\":\"DAILY\"}");
        mockMvc.perform(post("/api/goals/{id}/achieve", goal).header(HttpHeaders.AUTHORIZATION, token));

        mockMvc.perform(put("/api/habits/{id}/goal", habit).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\":" + goal + ",\"goalTargetDays\":30}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_ACTIVE"));
    }

    @Test
    void anotherUserGetsNotFoundAndTheGoalStaysActive() throws Exception {
        String alice = bearerFor("alice.status@example.com");
        String bob = bearerFor("bob.status@example.com");
        Integer goal = create("/api/goals", alice, "{\"title\":\"Mine\"}");

        List<MockHttpServletRequestBuilder> asBob = List.of(
                post("/api/goals/{id}/achieve", goal), post("/api/goals/{id}/abandon", goal));
        for (MockHttpServletRequestBuilder request : asBob) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_FOUND"));
        }
        mockMvc.perform(get("/api/goals/{id}", goal).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(jsonPath("$.payload.status").value("ACTIVE"));
    }

    private Integer create(String url, String token, String json) throws Exception {
        String body = mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }
}
