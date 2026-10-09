package com.nayeem.habittracker.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(new RateLimiter(Clock.systemUTC()),
            new JsonSecurityErrorHandler(JsonMapper.builder().build()));

    @Test
    void the11thLoginInAMinuteFromOneIpIsRefused() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(send("POST", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse refused = send("POST", "/api/auth/login", "10.0.0.1");

        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isNotBlank();
        assertThat(refused.getContentAsString()).contains("\"errorCode\":\"RATE_LIMITED\"");
        // another client, another endpoint, or a GET aren't affected
        assertThat(send("POST", "/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/auth/register", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(send("GET", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(send("POST", "/api/habits", "10.0.0.3").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse send(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
