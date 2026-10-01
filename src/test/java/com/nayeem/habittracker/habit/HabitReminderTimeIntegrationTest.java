package com.nayeem.habittracker.habit;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class HabitReminderTimeIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reminderTimeIsSetReadBackAndCleared() throws Exception {
        String token = bearerFor("reminder@example.com");
        String created = mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Read","frequencyType":"DAILY","reminderTime":"07:30"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.reminderTime").value("07:30"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.payload.id");

        mockMvc.perform(get("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.reminderTime").value("07:30"));

        // full replace: leaving it out clears the reminder, like category
        mockMvc.perform(put("/api/habits/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Read","frequencyType":"DAILY"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.reminderTime").doesNotExist());
    }

    @Test
    void invalidTimesAreRejected() throws Exception {
        String token = bearerFor("reminder.bad@example.com");
        for (String bad : new String[] {"25:00", "7:3", "07:30:15", "noon"}) {
            mockMvc.perform(post("/api/habits").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Read","frequencyType":"DAILY","reminderTime":"%s"}""".formatted(bad)))
                    .andExpect(status().isBadRequest());
        }
    }
}
