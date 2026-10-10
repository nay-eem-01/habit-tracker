package com.nayeem.habittracker.account;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.file.FileProperties;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Not {@code @Transactional}: the file bytes go after commit, so the delete has to really commit. */
class DeleteAccountIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FileProperties fileProperties;

    @Test
    void deletesEverythingOfTheUserAndNothingElse() throws Exception {
        String token = register("delete.me@example.com");
        String other = register("delete.other@example.com");
        Long userId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, "delete.me@example.com");
        Integer habit = id(send(token, "/api/habits", "{\"name\":\"Run\",\"frequencyType\":\"DAILY\"}"));
        send(token, "/api/habits/" + habit + "/checkin", "{}");
        Integer goal = id(send(token, "/api/goals", "{\"title\":\"5K\"}"));
        send(token, "/api/resources", "{\"type\":\"NOTE\",\"title\":\"Plan\",\"body\":\"b\",\"goalId\":" + goal + "}");
        String key = uploadFile(token);
        id(send(other, "/api/habits", "{\"name\":\"Read\",\"frequencyType\":\"DAILY\"}"));

        deleteAccount(token, "{\"password\":\"wrong-password\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_WRONG_PASSWORD"));
        deleteAccount(token, "{\"password\":\"password123\"}").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        for (String table : new String[]{"habits", "goals", "resources", "stored_files", "refresh_tokens",
                "one_time_tokens", "notifications"}) {
            assertThat(jdbcTemplate.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class,
                    userId)).as(table).isZero();
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from habit_logs where habit_id = ?", Long.class, habit))
                .isZero();
        assertThat(Files.exists(fileProperties.getDir().resolve(key))).isFalse();
        mockMvc.perform(get("/api/habits").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(jsonPath("$.payload.totalElements").value(1));
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"password123","name":"Test"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.payload.accessToken");
    }

    private String send(String token, String path, String json) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }

    private static Integer id(String body) {
        return JsonPath.read(body, "$.payload.id");
    }

    private String uploadFile(String token) throws Exception {
        byte[] pdf = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.ISO_8859_1);
        mockMvc.perform(multipart("/api/resources/files")
                        .file(new MockMultipartFile("file", "plan.pdf", "application/pdf", pdf))
                        .param("title", "Plan").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated());
        return jdbcTemplate.queryForObject("""
                select f.storage_key from stored_files f join users u on u.id = f.user_id where u.email = ?""",
                String.class, "delete.me@example.com");
    }

    private ResultActions deleteAccount(String token, String json) throws Exception {
        return mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
