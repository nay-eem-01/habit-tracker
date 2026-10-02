package com.nayeem.habittracker.resource;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GoalResourcesIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsOnlyThatGoalsResourcesPinnedFirst() throws Exception {
        String token = bearerFor("goal.res@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Run\"}");
        Integer other = create("/api/goals", token, "{\"title\":\"Read\"}");
        create("/api/resources", token, note("older", goal));
        Integer pinned = create("/api/resources", token, note("pinned", goal));
        create("/api/resources", token, note("newer", goal));
        create("/api/resources", token, note("elsewhere", other));
        create("/api/resources", token, "{\"type\":\"NOTE\",\"title\":\"loose\",\"body\":\"x\"}");
        mockMvc.perform(post("/api/resources/{id}/pin", pinned).header(HttpHeaders.AUTHORIZATION, token));

        mockMvc.perform(get("/api/goals/{id}/resources", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.totalElements").value(3))
                .andExpect(jsonPath("$.payload.content[0].title").value("pinned"))
                .andExpect(jsonPath("$.payload.content[1].title").value("newer"))
                .andExpect(jsonPath("$.payload.content[2].title").value("older"));
        mockMvc.perform(get("/api/goals/{id}/resources", goal).param("size", "2").param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.content.length()").value(1));
    }

    @Test
    void aGoalWithNothingKeptIsAnEmptyPageNotAnError() throws Exception {
        String token = bearerFor("goal.res.empty@example.com");
        Integer goal = create("/api/goals", token, "{\"title\":\"Run\"}");

        mockMvc.perform(get("/api/goals/{id}/resources", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.totalElements").value(0));
    }

    @Test
    void anotherUsersGoalIsNotFound() throws Exception {
        String alice = bearerFor("alice.goalres@example.com");
        String bob = bearerFor("bob.goalres@example.com");
        Integer goal = create("/api/goals", alice, "{\"title\":\"Mine\"}");
        create("/api/resources", alice, note("secret", goal));

        mockMvc.perform(get("/api/goals/{id}/resources", goal).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_FOUND"));
        mockMvc.perform(get("/api/goals/{id}/resources", 999_999).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isNotFound());
    }

    private static String note(String title, Integer goalId) {
        return "{\"type\":\"NOTE\",\"title\":\"" + title + "\",\"body\":\"x\",\"goalId\":" + goalId + "}";
    }

    private Integer create(String url, String token, String json) throws Exception {
        String body = mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }
}
