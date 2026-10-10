package com.nayeem.habittracker.push;

import com.nayeem.habittracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PushApiIntegrationTest extends IntegrationTest {

    private static final String FCM = "https://fcm.googleapis.com/fcm/send/abc123";
    private static final String P256DH = Base64.getUrlEncoder().withoutPadding().encodeToString(point());
    private static final String AUTH = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PushService pushService;
    @Autowired
    private PushProperties properties;
    @MockitoBean
    private WebPushSender sender;

    @Test
    void savesASubscriptionAndMovesItWhenAnotherUserSignsInThere() throws Exception {
        String alice = bearerFor("push.alice@example.com");
        mockMvc.perform(get("/api/push/public-key").header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(jsonPath("$.payload.enabled").value(false));

        subscribe(alice, FCM, P256DH, AUTH).andExpect(status().isCreated());
        subscribe(alice, FCM, P256DH, AUTH).andExpect(status().isCreated());   // again: fine
        subscribe(bearerFor("push.bob@example.com"), FCM, P256DH, AUTH).andExpect(status().isCreated());

        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("""
                select u.email from push_subscriptions p join users u on u.id = p.user_id where p.endpoint = ?""",
                String.class, FCM)).isEqualTo("push.bob@example.com");
    }

    @Test
    void onlyBrowserPushServicesAndRealKeys() throws Exception {
        String token = bearerFor("push.bad@example.com");
        for (String endpoint : new String[]{"https://evil.example.com/x", "http://fcm.googleapis.com/x",
                "https://fcm.googleapis.com.evil.com/x", "https://user@fcm.googleapis.com/x", "not a url"}) {
            subscribe(token, endpoint, P256DH, AUTH).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PUSH_SUBSCRIPTION_INVALID"));
        }
        subscribe(token, FCM, AUTH, AUTH).andExpect(jsonPath("$.errorCode").value("PUSH_SUBSCRIPTION_INVALID"));
        subscribe(token, "https://updates.push.services.mozilla.com/wpush/v2/x", P256DH, AUTH)
                .andExpect(status().isCreated());
    }

    @Test
    void aGoneSubscriptionIsDeletedAndNothingIsSentWhileOff() throws Exception {
        String token = bearerFor("push.gone@example.com");
        subscribe(token, FCM, P256DH, AUTH);
        Long userId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class,
                "push.gone@example.com");

        pushService.sendToUser(userId, "t", "b", "/");
        verify(sender, never()).send(any(), any());

        properties.setEnabled(true);
        try {
            when(sender.send(any(), any())).thenReturn(410);
            pushService.sendToUser(userId, "t", "b", "/");
        } finally {
            properties.setEnabled(false);
        }
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("select count(*) from push_subscriptions where user_id = ?",
                Long.class, userId)).isZero();
    }

    @Test
    void unsubscribeForgetsIt() throws Exception {
        String token = bearerFor("push.off@example.com");
        subscribe(token, FCM, P256DH, AUTH);
        mockMvc.perform(delete("/api/push/subscriptions").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + FCM + "\"}"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("select count(*) from push_subscriptions where endpoint = ?",
                Long.class, FCM)).isZero();
    }

    private ResultActions subscribe(String token, String endpoint, String p256dh, String auth) throws Exception {
        return mockMvc.perform(post("/api/push/subscriptions").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"endpoint":"%s","keys":{"p256dh":"%s","auth":"%s"}}""".formatted(endpoint, p256dh, auth)));
    }

    private static byte[] point() {
        byte[] point = new byte[65];
        point[0] = 4;
        return point;
    }
}
