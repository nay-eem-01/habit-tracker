package com.nayeem.habittracker.user;

import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Test
    void setsShowsAndRemovesTheProfilePicture() throws Exception {
        String token = bearerFor("avatar@example.com");
        mockMvc.perform(get("/api/me/avatar").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_AVATAR_NOT_FOUND"));

        byte[] png = AvatarImageTest.encode(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png");
        uploadAvatar(token, new MockMultipartFile("file", "me.png", "image/png", png))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.avatarUpdatedAt").isNotEmpty());
        mockMvc.perform(get("/api/me/avatar").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.avatarUpdatedAt").isNotEmpty());

        mockMvc.perform(delete("/api/me/avatar").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/avatar").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.avatarUpdatedAt").value(nullValue()));
    }

    @Test
    void refusesWhatIsNotAPicture() throws Exception {
        String token = bearerFor("avatar.bad@example.com");
        uploadAvatar(token, new MockMultipartFile("file", "me.png", "image/png",
                "<html><script>1</script>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("USER_AVATAR_INVALID"));
    }

    @Test
    void avatarNeedsASignIn() throws Exception {
        mockMvc.perform(get("/api/me/avatar")).andExpect(status().isUnauthorized());
    }

    private ResultActions uploadAvatar(String token, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/me/avatar").file(file)
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                })
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions update(String token, String json) throws Exception {
        return mockMvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
