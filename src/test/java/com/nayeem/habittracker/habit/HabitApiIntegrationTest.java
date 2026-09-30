package com.nayeem.habittracker.habit;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class HabitApiIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createThenGet() throws Exception {
        String alice = bearerFor("alice.api@example.com");
        String body = create(alice, """
                {"name":" Gym ","category":"Health","frequencyType":"SPECIFIC_DAYS",
                 "frequencyConfig":{"days":["FRIDAY","MONDAY"]}}""")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/habits/")))
                .andExpect(jsonPath("$.payload.name").value("Gym"))
                .andExpect(jsonPath("$.payload.frequencyConfig.days[0]").value("MONDAY"))
                .andExpect(jsonPath("$.payload.targetCount").value(1))
                .andExpect(jsonPath("$.payload.archived").value(false))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.payload.id");

        mockMvc.perform(get("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.frequencyType").value("SPECIFIC_DAYS"))
                .andExpect(jsonPath("$.payload.createdAt").isNotEmpty());
    }

    @Test
    void timesPerWeekHabit() throws Exception {
        create(bearerFor("weekly@example.com"), """
                {"name":"Swim","frequencyType":"X_TIMES_PER_WEEK","frequencyConfig":{"timesPerWeek":3},"targetCount":2}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.frequencyConfig.timesPerWeek").value(3))
                .andExpect(jsonPath("$.payload.frequencyConfig.days").doesNotExist())
                .andExpect(jsonPath("$.payload.targetCount").value(2));
    }

    @Test
    void scheduleThatDoesNotFitTheTypeIs400() throws Exception {
        create(bearerFor("badfreq@example.com"), """
                {"name":"Gym","frequencyType":"SPECIFIC_DAYS"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("HABIT_INVALID_FREQUENCY"));
    }

    @Test
    void validationAndUnknownEnum() throws Exception {
        String token = bearerFor("valid@example.com");
        create(token, """
                {"name":"","frequencyType":"DAILY","targetCount":0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.targetCount").exists());
        create(token, """
                {"name":"X","frequencyType":"HOURLY"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void someoneElsesHabitIsNotFound() throws Exception {
        String alice = bearerFor("alice.own@example.com");
        String bob = bearerFor("bob.own@example.com");
        Integer id = JsonPath.read(create(alice, daily("Read")).andReturn().getResponse().getContentAsString(),
                "$.payload.id");

        mockMvc.perform(get("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HABIT_NOT_FOUND"));
        mockMvc.perform(get("/api/habits").header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(jsonPath("$.payload.totalElements").value(0));
    }

    @Test
    void listIsPagedAndSortable() throws Exception {
        String token = bearerFor("lister@example.com");
        create(token, daily("Bravo"));
        create(token, daily("Alpha"));
        create(token, daily("Charlie"));

        mockMvc.perform(get("/api/habits").param("sortBy", "name").param("sortDir", "asc").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(2))
                .andExpect(jsonPath("$.payload.content[0].name").value("Alpha"))
                .andExpect(jsonPath("$.payload.totalElements").value(3))
                .andExpect(jsonPath("$.payload.totalPages").value(2));

        mockMvc.perform(get("/api/habits").param("sortBy", "user").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsAToken() throws Exception {
        mockMvc.perform(get("/api/habits")).andExpect(status().isUnauthorized());
    }

    private ResultActions create(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String daily(String name) {
        return """
                {"name":"%s","frequencyType":"DAILY"}""".formatted(name);
    }
}
