package com.nayeem.habittracker.admin;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Not {@code @Transactional}: deleting a user has to really commit. */
class AdminUsersIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anAdminFindsAUserAndSeesTheirCountsButNotTheirContent() throws Exception {
        String admin = admin("admin.view.admin@example.com");
        String user = register("admin.view.user@example.com");
        Integer habit = JsonPath.read(send(user, "/api/habits", "{\"name\":\"Secret habit\",\"frequencyType\":\"DAILY\"}"),
                "$.payload.id");
        send(user, "/api/habits/" + habit + "/checkin", "{}");
        long id = idOf("admin.view.user@example.com");

        mockMvc.perform(get("/api/admin/users").param("q", "VIEW.user@").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.totalElements").value(1))
                .andExpect(jsonPath("$.payload.content[0].email").value("admin.view.user@example.com"))
                .andExpect(jsonPath("$.payload.content[0].role").value("USER"));
        mockMvc.perform(get("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.user.plan").value("FREE"))
                .andExpect(jsonPath("$.payload.activeHabits").value(1))
                .andExpect(jsonPath("$.payload.checkIns").value(1))
                .andExpect(jsonPath("$.payload.lastActiveAt").value(notNullValue()))
                .andExpect(jsonPath("$.payload.pushSubscriptions").value(0));

        mockMvc.perform(get("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/users/999999999").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void anAdminChangesAPlanAndSuspendsAndLiftsIt() throws Exception {
        String admin = admin("admin.act.admin@example.com");
        String user = register("admin.act.user@example.com");
        long id = idOf("admin.act.user@example.com");

        mockMvc.perform(put("/api/admin/users/" + id + "/plan").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.plan").value("PRO"));

        action(admin, id, "suspend").andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.suspendedAt").value(notNullValue()));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, user)).andExpect(status().isUnauthorized());
        login("admin.act.user@example.com").andExpect(jsonPath("$.errorCode").value("AUTH_ACCOUNT_SUSPENDED"));

        action(admin, id, "unsuspend").andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.suspendedAt").doesNotExist());
        login("admin.act.user@example.com").andExpect(status().isOk());
    }

    @Test
    void anAdminDeletesAUserButNeverAnAdmin() throws Exception {
        String admin = admin("admin.del.admin@example.com");
        register("admin.del.user@example.com");
        long id = idOf("admin.del.user@example.com");
        long self = idOf("admin.del.admin@example.com");

        action(admin, self, "suspend").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_TARGET_IS_ADMIN"));
        mockMvc.perform(delete("/api/admin/users/" + self).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    private String admin(String email) throws Exception {
        String token = register(email);
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", email);
        return token;
    }

    private long idOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"password123","name":"Test"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.payload.accessToken");
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"password123"}""".formatted(email)));
    }

    private ResultActions action(String admin, long id, String action) throws Exception {
        return mockMvc.perform(post("/api/admin/users/" + id + "/" + action).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private String send(String token, String path, String json) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }
}
