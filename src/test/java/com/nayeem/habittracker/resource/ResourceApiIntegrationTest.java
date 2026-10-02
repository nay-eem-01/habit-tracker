package com.nayeem.habittracker.resource;

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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ResourceApiIntegrationTest extends IntegrationTest {

    private static final String NOTE = """
            {"type":"NOTE","title":"Week 1 plan","body":"# Run\\n- Mon 20 min"}""";
    private static final String LINK = """
            {"type":"LINK","title":"Couch to 5K","url":"https://example.com/c25k"}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsANoteAndALink() throws Exception {
        String token = bearerFor("res.create@example.com");

        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"NOTE","title":"  Week 1 plan ","body":"# Run"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/resources/")))
                .andExpect(jsonPath("$.payload.type").value("NOTE"))
                .andExpect(jsonPath("$.payload.title").value("Week 1 plan"))
                .andExpect(jsonPath("$.payload.body").value("# Run"))
                .andExpect(jsonPath("$.payload.url").doesNotExist())
                .andExpect(jsonPath("$.payload.goalId").doesNotExist())
                .andExpect(jsonPath("$.payload.pinned").value(false));
        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"LINK","title":"Couch to 5K","url":"https://example.com/c25k","body":"the one I use"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.url").value("https://example.com/c25k"))
                .andExpect(jsonPath("$.payload.body").value("the one I use"));
    }

    @Test
    void theFieldsMustFitTheType() throws Exception {
        String token = bearerFor("res.invalid@example.com");

        for (String body : List.of(
                """
                {"type":"NOTE","title":"No body"}""",
                """
                {"type":"NOTE","title":"Blank body","body":"   "}""",
                """
                {"type":"NOTE","title":"Has url","body":"x","url":"https://example.com"}""",
                """
                {"type":"LINK","title":"No url"}""")) {
            mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_INVALID"));
        }
    }

    @Test
    void aLinkMustBeAnHttpAddress() throws Exception {
        String token = bearerFor("res.url@example.com");

        for (String url : List.of("javascript:alert(1)", "ftp://example.com/x", "file:///etc/passwd",
                "data:text/html;base64,PHNjcmlwdD4=", "//evil.example.com", "example.com/no-scheme",
                "https://user:secret@example.com/", "http:///no-host", "https://exa mple.com")) {
            mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"LINK\",\"title\":\"x\",\"url\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_INVALID"));
        }
        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LINK\",\"title\":\"ok\",\"url\":\"HTTP://Example.com/a?b=1#c\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void limitsTheSizeOfEveryField() throws Exception {
        String token = bearerFor("res.size@example.com");

        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOTE\",\"title\":\"" + "t".repeat(201) + "\",\"body\":\"" + "b".repeat(20_001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.body").exists());
        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOTE\",\"title\":\"max\",\"body\":\"" + "b".repeat(20_000) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void aResourceCanBelongToMyGoalButNotSomeoneElses() throws Exception {
        String alice = bearerFor("alice.res@example.com");
        String bob = bearerFor("bob.res@example.com");
        Integer aliceGoal = createGoal(alice);

        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, alice)
                        .contentType(MediaType.APPLICATION_JSON).content(withGoal(LINK, aliceGoal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.goalId").value(aliceGoal));
        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, bob)
                        .contentType(MediaType.APPLICATION_JSON).content(withGoal(LINK, aliceGoal)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_FOUND"));
    }

    @Test
    void updateReplacesEverythingIncludingTheType() throws Exception {
        String token = bearerFor("res.update@example.com");
        Integer goal = createGoal(token);
        Integer id = create(token, withGoal(LINK, goal));

        mockMvc.perform(put("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(NOTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.type").value("NOTE"))
                .andExpect(jsonPath("$.payload.title").value("Week 1 plan"))
                .andExpect(jsonPath("$.payload.url").doesNotExist())
                .andExpect(jsonPath("$.payload.goalId").doesNotExist())
                .andExpect(jsonPath("$.payload.pinned").value(false));
    }

    @Test
    void pinnedComeFirstThenNewest() throws Exception {
        String token = bearerFor("res.order@example.com");
        Integer first = create(token, titled("first"));
        create(token, titled("second"));
        create(token, titled("third"));

        mockMvc.perform(post("/api/resources/{id}/pin", first).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.pinned").value(true));
        mockMvc.perform(post("/api/resources/{id}/pin", first).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.content[0].title").value("first"))
                .andExpect(jsonPath("$.payload.content[1].title").value("third"))
                .andExpect(jsonPath("$.payload.content[2].title").value("second"));

        mockMvc.perform(post("/api/resources/{id}/unpin", first).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.pinned").value(false));
        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.content[0].title").value("third"));
    }

    @Test
    void listFiltersByGoalTypeAndTitleAndPages() throws Exception {
        String token = bearerFor("res.filter@example.com");
        Integer goal = createGoal(token);
        create(token, withGoal(LINK, goal));
        create(token, NOTE);
        create(token, titled("100% sure_thing"));
        create(token, titled("Plain"));

        mockMvc.perform(get("/api/resources").param("goalId", goal.toString()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(1))
                .andExpect(jsonPath("$.payload.content[0].title").value("Couch to 5K"));
        mockMvc.perform(get("/api/resources").param("type", "LINK").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(1));
        mockMvc.perform(get("/api/resources").param("q", "WEEK").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(1));
        // % and _ are plain characters in a search, not wildcards
        mockMvc.perform(get("/api/resources").param("q", "%").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(1));
        mockMvc.perform(get("/api/resources").param("q", "sure_").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(1));
        mockMvc.perform(get("/api/resources").param("q", "s_re").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(0));
        mockMvc.perform(get("/api/resources").param("size", "3").param("page", "1").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.content.length()").value(1))
                .andExpect(jsonPath("$.payload.totalPages").value(2));
        mockMvc.perform(get("/api/resources").param("type", "FILE").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesItForGood() throws Exception {
        String token = bearerFor("res.delete@example.com");
        Integer id = create(token, NOTE);

        mockMvc.perform(delete("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    /** Every resource endpoint, with someone else's token: always 404, never the resource. */
    @Test
    void anotherUserCanNeitherSeeNorChangeIt() throws Exception {
        String alice = bearerFor("alice.res2@example.com");
        String bob = bearerFor("bob.res2@example.com");
        Integer id = create(alice, NOTE);

        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/resources/{id}", id),
                put("/api/resources/{id}", id).contentType(MediaType.APPLICATION_JSON).content(LINK),
                post("/api/resources/{id}/pin", id),
                post("/api/resources/{id}/unpin", id),
                delete("/api/resources/{id}", id));
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bob))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }
        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(jsonPath("$.payload.totalElements").value(0));

        mockMvc.perform(get("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(jsonPath("$.payload.title").value("Week 1 plan"))
                .andExpect(jsonPath("$.payload.pinned").value(false));
    }

    @Test
    void needsASignedInUser() throws Exception {
        mockMvc.perform(get("/api/resources")).andExpect(status().isUnauthorized());
    }

    private static String titled(String title) {
        return "{\"type\":\"NOTE\",\"title\":\"" + title + "\",\"body\":\"text\"}";
    }

    private static String withGoal(String json, Integer goalId) {
        return json.substring(0, json.lastIndexOf('}')) + ",\"goalId\":" + goalId + "}";
    }

    private Integer createGoal(String token) throws Exception {
        String body = mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Run\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }

    private Integer create(String token, String json) throws Exception {
        String body = mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }
}
