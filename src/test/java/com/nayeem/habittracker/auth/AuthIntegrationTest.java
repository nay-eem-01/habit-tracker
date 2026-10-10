package com.nayeem.habittracker.auth;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerSignsInAndTheTokenWorksOnMe() throws Exception {
        String body = register("new@example.com", "password123", "Asia/Dhaka")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.payload.expiresIn").value(900))
                .andExpect(jsonPath("$.payload.user.email").value("new@example.com"))
                .andExpect(jsonPath("$.payload.user.timezone").value("Asia/Dhaka"))
                .andExpect(content().string(not(containsString("password"))))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.payload.accessToken");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.email").value("new@example.com"))
                .andExpect(jsonPath("$.payload.name").value("Nayeem"));
    }

    @Test
    void registerTwiceIsConflict() throws Exception {
        register("twice@example.com", "password123", null).andExpect(status().isCreated());
        register("TWICE@example.com", "password123", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_EMAIL_TAKEN"));
    }

    @Test
    void registerValidatesFields() throws Exception {
        register("not-an-email", "short", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void registerRejectsUnknownTimezone() throws Exception {
        register("tz@example.com", "password123", "Mars/Olympus")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_INVALID_TIMEZONE"));
    }

    @Test
    void loginWithRightPassword() throws Exception {
        register("login@example.com", "password123", null);
        login("Login@Example.com", "password123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.accessToken").isNotEmpty());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        register("known@example.com", "password123", null);
        login("known@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_CREDENTIALS"));
        login("unknown@example.com", "password123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void fiveWrongPasswordsLockTheEmailForAWhile() throws Exception {
        register("locked@example.com", "password123", null);
        for (int i = 0; i < 5; i++) {
            login("locked@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }
        login("LOCKED@example.com", "password123")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));
    }

    private ResultActions register(String email, String password, String timezone) throws Exception {
        String tz = timezone == null ? "null" : "\"" + timezone + "\"";
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","name":"Nayeem","timezone":%s}""".formatted(email, password, tz)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)));
    }
}
