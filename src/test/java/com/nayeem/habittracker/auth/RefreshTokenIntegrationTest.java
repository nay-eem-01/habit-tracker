package com.nayeem.habittracker.auth;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.nayeem.habittracker.auth.AuthController.REFRESH_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RefreshTokenIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository repository;

    @Test
    void loginSetsAHardenedCookieAndKeepsTheTokenOutOfTheBody() throws Exception {
        MockHttpServletResponse response = register("cookie@example.com")
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(cookie().secure(REFRESH_COOKIE, true))
                .andExpect(cookie().sameSite(REFRESH_COOKIE, "Strict"))
                .andExpect(cookie().path(REFRESH_COOKIE, "/api/auth"))
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 7 * 24 * 3600))
                .andReturn().getResponse();

        String raw = response.getCookie(REFRESH_COOKIE).getValue();
        assertThat(response.getContentAsString()).doesNotContain(raw);
        // stored hashed, never raw
        assertThat(repository.findByTokenHash(raw)).isEmpty();
        assertThat(repository.findByTokenHash(RefreshTokenService.hash(raw))).isPresent();
    }

    /** Plan §4.4 definition of done, minus the wait for expiry. */
    @Test
    void registerMeRefreshLogoutThenRefreshFails() throws Exception {
        Cookie first = register("loop@example.com").andReturn().getResponse().getCookie(REFRESH_COOKIE);

        MockHttpServletResponse refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.accessToken").isNotEmpty())
                .andReturn().getResponse();
        Cookie second = refreshed.getCookie(REFRESH_COOKIE);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        String accessToken = JsonPath.read(refreshed.getContentAsString(), "$.payload.accessToken");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").cookie(second))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void reusingARotatedTokenRevokesEveryTokenOfThatUser() throws Exception {
        Cookie first = register("reuse@example.com").andReturn().getResponse().getCookie(REFRESH_COOKIE);
        Cookie second = refresh(first).andReturn().getResponse().getCookie(REFRESH_COOKIE);

        refresh(first).andExpect(status().isUnauthorized());   // the copied, already-rotated token
        refresh(second).andExpect(status().isUnauthorized());  // so the live one died with it
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Cookie cookie = register("expired@example.com").andReturn().getResponse().getCookie(REFRESH_COOKIE);
        RefreshToken stored = repository.findByTokenHash(RefreshTokenService.hash(cookie.getValue())).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        repository.flush();

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrUnknownCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_REFRESH_TOKEN"));
        refresh(new Cookie(REFRESH_COOKIE, "made-up")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutACookieIsStillNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void refreshResponseDoesNotLeakTheRefreshToken() throws Exception {
        Cookie cookie = register("leak@example.com").andReturn().getResponse().getCookie(REFRESH_COOKIE);
        MockHttpServletResponse response = refresh(cookie).andReturn().getResponse();
        String newToken = response.getCookie(REFRESH_COOKIE).getValue();
        assertThat(response.getContentAsString()).doesNotContain(newToken);
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"password123","name":"Nayeem"}""".formatted(email)));
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(cookie));
    }
}
