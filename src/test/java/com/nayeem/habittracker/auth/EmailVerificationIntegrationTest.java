package com.nayeem.habittracker.auth;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.notification.OutgoingNotification;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Email verification (roadmap 8.1b). The email is captured as the event the mail channel would send. */
@Transactional
@RecordApplicationEvents
class EmailVerificationIntegrationTest extends IntegrationTest {

    private static final Pattern LINK = Pattern.compile("(http\\S+/verify-email)#token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationEvents events;

    @Test
    void signUpSendsALinkThatConfirmsTheEmailOnce() throws Exception {
        String bearer = register("verify.ok@example.com");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.payload.emailVerified").value(false));

        OutgoingNotification email = events.stream(OutgoingNotification.class).findFirst().orElseThrow();
        assertThat(email.email()).isEqualTo("verify.ok@example.com");
        assertThat(email.title()).isEqualTo("Confirm your Habit Tracker email");
        Matcher link = LINK.matcher(email.body());
        assertThat(link.find()).isTrue();
        assertThat(link.group(1)).isEqualTo("http://localhost:5173/verify-email");

        verify(link.group(2)).andExpect(status().isOk());   // no sign-in needed
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.payload.emailVerified").value(true));
        verify(link.group(2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_VERIFY_TOKEN"));
        // nothing left to confirm
        resend(bearer).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AUTH_EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void aResetLinkDoesNotConfirmAnEmail() throws Exception {
        register("verify.purpose@example.com");
        mockMvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"verify.purpose@example.com\"}"));
        List<OutgoingNotification> emails = events.stream(OutgoingNotification.class).toList();
        Matcher reset = Pattern.compile("#token=([A-Za-z0-9_-]+)").matcher(emails.get(1).body());
        assertThat(reset.find()).isTrue();

        verify(reset.group(1)).andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_VERIFY_TOKEN"));
    }

    @Test
    void resendingNeedsASignIn() throws Exception {
        mockMvc.perform(post("/api/auth/email/verification")).andExpect(status().isUnauthorized());
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"password123","name":"Test"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.payload.accessToken");
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions resend(String bearer) throws Exception {
        return mockMvc.perform(post("/api/auth/email/verification").header(HttpHeaders.AUTHORIZATION, bearer));
    }
}
