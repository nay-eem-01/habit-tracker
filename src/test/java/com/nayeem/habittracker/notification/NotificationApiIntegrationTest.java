package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.support.IntegrationTest;
import com.nayeem.habittracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class NotificationApiIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserService userService;

    @Test
    void listsUnreadFirstThenNewest() throws Exception {
        String token = bearerFor("notify.list@example.com");
        String email = "notify.list@example.com";
        Notification read = save(email, "Old, read");
        read.setReadAt(Instant.now());
        notificationRepository.saveAndFlush(read);
        save(email, "Unread 1");
        save(email, "Unread 2");

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.totalElements").value(3))
                .andExpect(jsonPath("$.payload.content[0].title").value("Unread 2"))
                .andExpect(jsonPath("$.payload.content[1].title").value("Unread 1"))
                .andExpect(jsonPath("$.payload.content[2].title").value("Old, read"))
                .andExpect(jsonPath("$.payload.content[2].readAt").exists())
                .andExpect(jsonPath("$.payload.content[0].readAt").doesNotExist());

        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.unread").value(2));
    }

    @Test
    void markReadIsIdempotentAndKeepsTheFirstTime() throws Exception {
        String token = bearerFor("notify.read@example.com");
        Long id = save("notify.read@example.com", "Read me").getId();

        String first = mockMvc.perform(post("/api/notifications/{id}/read", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.readAt").exists())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/notifications/{id}/read", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                com.jayway.jsonpath.JsonPath.<String>read(first, "$.payload.readAt"),
                com.jayway.jsonpath.JsonPath.<String>read(second, "$.payload.readAt"));
    }

    @Test
    void readAllMarksOnlyMine() throws Exception {
        String mine = bearerFor("notify.all@example.com");
        String other = bearerFor("notify.all.other@example.com");
        save("notify.all@example.com", "A");
        save("notify.all@example.com", "B");
        save("notify.all.other@example.com", "Not mine");

        mockMvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.marked").value(2));
        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, mine))
                .andExpect(jsonPath("$.payload.unread").value(0));
        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(jsonPath("$.payload.unread").value(1));
    }

    @Test
    void anotherUsersNotificationIs404AndStaysUnread() throws Exception {
        bearerFor("notify.owner@example.com");
        String intruder = bearerFor("notify.intruder@example.com");
        Notification n = save("notify.owner@example.com", "Private");

        mockMvc.perform(post("/api/notifications/{id}/read", n.getId()).header(HttpHeaders.AUTHORIZATION, intruder))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_NOT_FOUND"));
        org.junit.jupiter.api.Assertions.assertNull(notificationRepository.findById(n.getId()).orElseThrow().getReadAt());
    }

    @Test
    void requiresSignIn() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    private Notification save(String email, String title) {
        Notification n = new Notification();
        n.setUser(userService.findByEmail(email).orElseThrow());
        n.setType(NotificationType.HABIT_REMINDER);
        n.setTitle(title);
        n.setBody("Body");
        return notificationRepository.saveAndFlush(n);
    }
}
