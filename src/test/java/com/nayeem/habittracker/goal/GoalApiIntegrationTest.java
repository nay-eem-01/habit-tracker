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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GoalApiIntegrationTest extends IntegrationTest {

    private static final String HALF_MARATHON = """
            {"title":"Run a half marathon"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoalRepository goalRepository;

    @Test
    void createReturnsTheGoalAndItsLocation() throws Exception {
        String token = bearerFor("goal.create@example.com");

        mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"  Run a half marathon ","description":"Under two hours","targetDate":"2026-12-31"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/goals/")))
                .andExpect(jsonPath("$.payload.title").value("Run a half marathon"))
                .andExpect(jsonPath("$.payload.description").value("Under two hours"))
                .andExpect(jsonPath("$.payload.targetDate").value("2026-12-31"))
                .andExpect(jsonPath("$.payload.status").value("ACTIVE"))
                .andExpect(jsonPath("$.payload.achievedAt").doesNotExist());
    }

    @Test
    void createRejectsABlankOrTooLongTitle() throws Exception {
        String token = bearerFor("goal.invalid@example.com");

        mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").exists());
        mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists());
    }

    @Test
    void updateReplacesTheDetailsButNotTheStatus() throws Exception {
        String token = bearerFor("goal.update@example.com");
        Integer id = createGoal(token, """
                {"title":"Run","description":"Old","targetDate":"2026-12-31"}""");

        mockMvc.perform(put("/api/goals/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Run a marathon"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.title").value("Run a marathon"))
                .andExpect(jsonPath("$.payload.description").doesNotExist())
                .andExpect(jsonPath("$.payload.targetDate").doesNotExist())
                .andExpect(jsonPath("$.payload.status").value("ACTIVE"));
    }

    @Test
    void listIsPagedAndFiltersByStatus() throws Exception {
        String token = bearerFor("goal.list@example.com");
        // achieved before the others: the free plan keeps 2 goals active at a time
        Integer done = createGoal(token, """
                {"title":"Read 12 books"}""");
        goalRepository.findById(done.longValue()).orElseThrow().setStatus(GoalStatus.ACHIEVED);
        goalRepository.flush();
        createGoal(token, HALF_MARATHON);
        createGoal(token, """
                {"title":"Learn Spanish"}""");

        mockMvc.perform(get("/api/goals").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(3));
        mockMvc.perform(get("/api/goals").param("status", "ACTIVE").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(2));
        mockMvc.perform(get("/api/goals").param("status", "ACHIEVED").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(1))
                .andExpect(jsonPath("$.payload.content[0].title").value("Read 12 books"));
        mockMvc.perform(get("/api/goals").param("size", "2").param("sortBy", "title").param("sortDir", "asc")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.content.length()").value(2))
                .andExpect(jsonPath("$.payload.totalPages").value(2))
                .andExpect(jsonPath("$.payload.content[0].title").value("Learn Spanish"));
    }

    @Test
    void listRejectsAnUnknownStatusAndSortColumn() throws Exception {
        String token = bearerFor("goal.badparam@example.com");

        mockMvc.perform(get("/api/goals").param("status", "DONE").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/goals").param("sortBy", "user").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    /** Every goal endpoint, with someone else's token: always 404, never the goal. */
    @Test
    void anotherUserCanNeitherSeeNorChangeIt() throws Exception {
        String alice = bearerFor("alice.goal@example.com");
        String bob = bearerFor("bob.goal@example.com");
        Integer id = createGoal(alice, HALF_MARATHON);

        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/goals/{id}", id),
                put("/api/goals/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Hijacked"}"""));
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_FOUND"));
        }
        mockMvc.perform(get("/api/goals").header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(jsonPath("$.payload.totalElements").value(0));

        mockMvc.perform(get("/api/goals/{id}", id).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(jsonPath("$.payload.title").value("Run a half marathon"));
    }

    @Test
    void needsASignedInUser() throws Exception {
        mockMvc.perform(get("/api/goals")).andExpect(status().isUnauthorized());
    }

    private Integer createGoal(String token, String json) throws Exception {
        String body = mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }
}
