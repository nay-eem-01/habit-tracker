package com.nayeem.habittracker.auth;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static com.nayeem.habittracker.auth.AuthController.REFRESH_COOKIE;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign in with Google (roadmap 2.3). The token checks are in GoogleIdTokenVerifierTest; here Google is mocked. */
@Transactional
class GoogleSignInIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private GoogleIdTokenVerifier verifier;

    @Test
    void createsAnAccountOnceThenSignsItIn() throws Exception {
        google("t1", new GoogleIdentity("sub-new", "New.Person@gmail.com", true, "New Person"));

        String body = signIn("t1", "Asia/Dhaka").andExpect(status().isOk())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andExpect(jsonPath("$.payload.user.email").value("new.person@gmail.com"))
                .andExpect(jsonPath("$.payload.user.name").value("New Person"))
                .andExpect(jsonPath("$.payload.user.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.payload.user.emailVerified").value(true))
                .andExpect(jsonPath("$.payload.user.timezone").value("Asia/Dhaka"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.payload.user.id");

        signIn("t1", null).andExpect(jsonPath("$.payload.user.id").value(id));
    }

    @Test
    void linksAVerifiedAccountAndItsPasswordKeepsWorking() throws Exception {
        register("linked@gmail.com");
        jdbcTemplate.update("update users set email_verified_at = now() where email = ?", "linked@gmail.com");
        entityManager.clear();
        google("t2", new GoogleIdentity("sub-linked", "linked@gmail.com", true, "Linked"));

        signIn("t2", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.user.authProvider").value("LOCAL"));
        login("linked@gmail.com").andExpect(status().isOk());
    }

    @Test
    void linkingAnUnverifiedAccountRemovesItsPasswordAndSessions() throws Exception {
        // someone signed up with this address without owning it (pre-hijacking)
        Cookie squatter = register("victim@gmail.com").andReturn().getResponse().getCookie(REFRESH_COOKIE);
        google("t3", new GoogleIdentity("sub-victim", "victim@gmail.com", true, "Victim"));

        signIn("t3", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.user.emailVerified").value(true));

        login("victim@gmail.com").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(squatter)).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnverifiedGoogleEmailIsRefused() throws Exception {
        google("t4", new GoogleIdentity("sub-unverified", "unverified@gmail.com", false, "U"));
        signIn("t4", null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_GOOGLE_EMAIL_UNVERIFIED"));
    }

    private void google(String token, GoogleIdentity identity) {
        when(verifier.verify(token)).thenReturn(identity);
    }

    private ResultActions signIn(String token, String timezone) throws Exception {
        String tz = timezone == null ? "" : ",\"timezone\":\"" + timezone + "\"";
        return mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + token + "\"" + tz + "}"));
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"password123","name":"Test"}""".formatted(email)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"password123"}""".formatted(email)));
    }
}
