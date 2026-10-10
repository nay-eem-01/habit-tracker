package com.nayeem.habittracker.account;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ExportApiIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsMyDataAndNobodyElses() throws Exception {
        String token = bearerFor("export@example.com");
        Integer habit = id(send(token, "/api/habits", "{\"name\":\"Run\",\"frequencyType\":\"DAILY\",\"targetCount\":2}"));
        send(token, "/api/habits/" + habit + "/checkin", "{\"note\":\"easy\"}");
        send(token, "/api/goals", "{\"title\":\"5K\"}");
        send(token, "/api/resources", "{\"type\":\"LINK\",\"title\":\"Plan\",\"url\":\"https://example.com\"}");
        send(bearerFor("export.other@example.com"), "/api/goals", "{\"title\":\"Not mine\"}");

        mockMvc.perform(get("/api/me/export").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("devhabit-export.json")))
                .andExpect(jsonPath("$.profile.email").value("export@example.com"))
                .andExpect(jsonPath("$.habits.length()").value(1))
                .andExpect(jsonPath("$.checkIns[0].habitId").value(habit))
                .andExpect(jsonPath("$.checkIns[0].completedCount").value(2))
                .andExpect(jsonPath("$.checkIns[0].targetCount").value(2))
                .andExpect(jsonPath("$.checkIns[0].note").value("easy"))
                .andExpect(jsonPath("$.goals.length()").value(1))
                .andExpect(jsonPath("$.goals[0].title").value("5K"))
                .andExpect(jsonPath("$.resources[0].url").value("https://example.com"));
    }

    private String send(String token, String path, String json) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }

    private static Integer id(String body) {
        return JsonPath.read(body, "$.payload.id");
    }
}
