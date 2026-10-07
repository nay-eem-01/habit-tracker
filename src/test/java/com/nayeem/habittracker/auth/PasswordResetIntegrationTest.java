package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.notification.OutgoingNotification;
import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nayeem.habittracker.auth.AuthController.REFRESH_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Forgot / reset password (roadmap 2.4a). The email is captured as the event the mail channel would send. */
@Transactional
@RecordApplicationEvents
class PasswordResetIntegrationTest extends IntegrationTest {

    private static final Pattern LINK = Pattern.compile("(http\\S+/reset-password)#token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void forgotThenResetSignsInWithTheNewPassword() throws Exception {
        register("reset.ok@example.com", "old-password");

        forgot("Reset.OK@example.com")   // any case, like login
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If an account exists for that email, a reset link is on its way"));
        OutgoingNotification email = onlyEmail();
        assertThat(email.email()).isEqualTo("reset.ok@example.com");
        assertThat(email.title()).isEqualTo("Reset your Habit Tracker password");
        Matcher link = LINK.matcher(email.body());
        assertThat(link.find()).isTrue();
        assertThat(link.group(1)).isEqualTo("http://localhost:5173/reset-password");
        assertThat(email.body()).contains("within 30 minutes");

        reset(link.group(2), "new-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.accessToken").isNotEmpty())
                .andExpect(cookie().exists(REFRESH_COOKIE));

        login("reset.ok@example.com", "new-password").andExpect(status().isOk());
        login("reset.ok@example.com", "old-password").andExpect(status().isUnauthorized());
        // single use
        reset(link.group(2), "another-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_RESET_TOKEN"));
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        forgot("nobody@example.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If an account exists for that email, a reset link is on its way"));
        assertThat(events.stream(OutgoingNotification.class)).isEmpty();
    }

    @Test
    void resettingSignsOutEveryOtherSession() throws Exception {
        Cookie elsewhere = register("reset.sessions@example.com", "old-password")
                .andReturn().getResponse().getCookie(REFRESH_COOKIE);
        forgot("reset.sessions@example.com");

        reset(token(onlyEmail()), "new-password").andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(elsewhere))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredOrMadeUpLinkIsRefused() throws Exception {
        register("reset.expired@example.com", "old-password");
        forgot("reset.expired@example.com");
        jdbcTemplate.update("update password_reset_tokens set expires_at = now() - interval '1 minute'"
                + " where user_id = (select id from users where email = ?)", "reset.expired@example.com");
        entityManager.clear();   // drop the cached token so the expired row is read

        reset(token(onlyEmail()), "new-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_RESET_TOKEN"));
        reset("made-up-token", "new-password")
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_RESET_TOKEN"));
    }

    @Test
    void usingOneLinkRetiresTheOthers() throws Exception {
        register("reset.two@example.com", "old-password");
        forgot("reset.two@example.com");
        backdateRequests("reset.two@example.com", "2 minutes");   // past the one-a-minute limit
        forgot("reset.two@example.com");
        List<OutgoingNotification> emails = events.stream(OutgoingNotification.class).toList();
        assertThat(emails).hasSize(2);

        reset(token(emails.get(1)), "new-password").andExpect(status().isOk());
        reset(token(emails.get(0)), "other-password")
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_RESET_TOKEN"));
    }

    @Test
    void atMostOneEmailAMinuteAndFiveAnHour() throws Exception {
        register("reset.limit@example.com", "old-password");
        forgot("reset.limit@example.com");
        forgot("reset.limit@example.com").andExpect(status().isAccepted());   // same answer, no email
        assertThat(events.stream(OutgoingNotification.class)).hasSize(1);

        backdateRequests("reset.limit@example.com", "10 minutes");
        for (int i = 0; i < 4; i++) {
            jdbcTemplate.update("""
                    insert into password_reset_tokens (user_id, token_hash, expires_at, created_at)
                    select id, md5(random()::text) || md5(random()::text), now(), now() - interval '20 minutes'
                    from users where email = ?""", "reset.limit@example.com");
        }
        forgot("reset.limit@example.com").andExpect(status().isAccepted());
        assertThat(events.stream(OutgoingNotification.class)).hasSize(1);
    }

    @Test
    void aGoogleOnlyAccountCanSetItsFirstPassword() throws Exception {
        register("reset.google@example.com", "unused-password");
        jdbcTemplate.update("update users set password_hash = null, auth_provider = 'GOOGLE' where email = ?",
                "reset.google@example.com");
        entityManager.clear();   // drop the cached user so the cleared password is read
        login("reset.google@example.com", "unused-password").andExpect(status().isUnauthorized());

        forgot("reset.google@example.com");
        OutgoingNotification email = onlyEmail();
        assertThat(email.title()).isEqualTo("Set your Habit Tracker password");
        assertThat(email.body()).contains("you sign in with Google today");

        reset(token(email), "first-password").andExpect(status().isOk());
        login("reset.google@example.com", "first-password").andExpect(status().isOk());
    }

    @Test
    void theNewPasswordFollowsTheSignUpRules() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.newPassword").exists());
        mockMvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"name\":\"Reset Test\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private OutgoingNotification onlyEmail() {
        List<OutgoingNotification> emails = events.stream(OutgoingNotification.class).toList();
        assertThat(emails).hasSize(1);
        return emails.getFirst();
    }

    private static String token(OutgoingNotification email) {
        Matcher link = LINK.matcher(email.body());
        assertThat(link.find()).isTrue();
        return link.group(2);
    }

    private void backdateRequests(String email, String interval) {
        jdbcTemplate.update("update password_reset_tokens set created_at = created_at - cast(? as interval)"
                + " where user_id = (select id from users where email = ?)", interval, email);
    }
}
