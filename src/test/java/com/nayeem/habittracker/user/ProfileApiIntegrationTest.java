package com.nayeem.habittracker.user;

import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ProfileApiIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void updatesNameTimezoneAndPromotionalEmailChoice() throws Exception {
        String token = bearerFor("profile@example.com");

        update(token, "{\"name\":\" Nayeem \",\"timezone\":\"Asia/Dhaka\",\"marketingEmails\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.name").value("Nayeem"))
                .andExpect(jsonPath("$.payload.timezone").value("Asia/Dhaka"))
                .andExpect(jsonPath("$.payload.marketingEmails").value(true))
                .andExpect(jsonPath("$.payload.email").value("profile@example.com"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.timezone").value("Asia/Dhaka"));
    }

    @Test
    void refusesBadFields() throws Exception {
        String token = bearerFor("profile.bad@example.com");
        update(token, "{\"name\":\"N\",\"timezone\":\"Asia/Dhaka\",\"marketingEmails\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
        update(token, "{\"name\":\"Nayeem\",\"timezone\":\"+06:00\",\"marketingEmails\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_INVALID_TIMEZONE"));
        update(token, "{\"name\":\"Nayeem\",\"timezone\":\"Asia/Dhaka\"}")
                .andExpect(jsonPath("$.fields.marketingEmails").exists());
    }

    @Test
    void needsASignIn() throws Exception {
        mockMvc.perform(put("/api/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions update(String token, String json) throws Exception {
        return mockMvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
