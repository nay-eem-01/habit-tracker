package com.nayeem.habittracker.auth;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.notification.OutgoingNotification;
import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nayeem.habittracker.auth.AuthController.REFRESH_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /api/auth/password/change} (roadmap 2.4b). */
@Transactional
@RecordApplicationEvents
class ChangePasswordIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void changesThePasswordAndSignsOutOtherSessions() throws Exception {
        MockHttpServletResponse signUp = register("change.ok@example.com");
        String token = accessToken(signUp);
        Cookie otherSession = signUp.getCookie(REFRESH_COOKIE);

        Cookie thisSession = change(token, "{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.accessToken").isNotEmpty())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andReturn().getResponse().getCookie(REFRESH_COOKIE);

        login("change.ok@example.com", "new-password").andExpect(status().isOk());
        login("change.ok@example.com", "old-password").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(otherSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(thisSession)).andExpect(status().isOk());
    }

    @Test
    void aWrongOrMissingCurrentPasswordChangesNothing() throws Exception {
        String token = accessToken(register("change.wrong@example.com"));

        change(token, "{\"currentPassword\":\"not-it-at-all\",\"newPassword\":\"new-password\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_WRONG_PASSWORD"));
        change(token, "{\"newPassword\":\"new-password\"}")
                .andExpect(jsonPath("$.errorCode").value("AUTH_WRONG_PASSWORD"));
        login("change.wrong@example.com", "old-password").andExpect(status().isOk());
    }

    @Test
    void aGoogleOnlyAccountIsSentToForgotPasswordForNow() throws Exception {
        String token = accessToken(register("change.google@example.com"));
        jdbcTemplate.update("update users set password_hash = null, auth_provider = 'GOOGLE' where email = ?",
                "change.google@example.com");
        entityManager.clear();   // drop the cached user so the cleared password is read

        change(token, "{\"newPassword\":\"first-password\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AUTH_PASSWORD_NOT_SET"));
    }

    @Test
    void aResetLinkSentBeforeTheChangeStopsWorking() throws Exception {
        String token = accessToken(register("change.link@example.com"));
        mockMvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"change.link@example.com\"}"))
                .andExpect(status().isAccepted());
        Matcher link = Pattern.compile("#token=([A-Za-z0-9_-]+)")
                .matcher(events.stream(OutgoingNotification.class).findFirst().orElseThrow().body());
        assertThat(link.find()).isTrue();

        change(token, "{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}")
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + link.group(1) + "\",\"newPassword\":\"sneaky-password\"}"))
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_RESET_TOKEN"));
    }

    @Test
    void needsASignInAndAValidNewPassword() throws Exception {
        mockMvc.perform(post("/api/auth/password/change").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isUnauthorized());
        String token = accessToken(register("change.short@example.com"));
        change(token, "{\"currentPassword\":\"old-password\",\"newPassword\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    private MockHttpServletResponse register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"old-password\",\"name\":\"Change Test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
    }

    private static String accessToken(MockHttpServletResponse response) throws Exception {
        return "Bearer " + JsonPath.read(response.getContentAsString(), "$.payload.accessToken");
    }

    private ResultActions change(String bearer, String json) throws Exception {
        return mockMvc.perform(post("/api/auth/password/change").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
