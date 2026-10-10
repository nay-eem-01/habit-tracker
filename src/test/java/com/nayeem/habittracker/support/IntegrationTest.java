package com.nayeem.habittracker.support;

import com.nayeem.habittracker.security.JwtService;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Base class for integration tests: the full application against the PostgreSQL container, with
 * MockMvc. Extend it rather than repeating the annotations, so every integration test shares one
 * cached context (and one container).
 */
@SpringBootTest(properties = {"app.security.jwt.secret=" + IntegrationTest.TEST_JWT_SECRET,
        "app.reminders.enabled=false", "app.files.enabled=true", "app.files.dir=target/test-files",
        // the tests share one client IP; AuthRateLimitFilterTest covers the limits
        "app.security.rate-limits.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String TEST_JWT_SECRET = "test-only-secret-at-least-32-bytes-long!!";

    @Autowired
    private UserService testUserService;

    @Autowired
    private JwtService testJwtService;

    /** Creates a user and returns an {@code Authorization} header value for them. */
    protected String bearerFor(String email) {
        User user = testUserService.createLocalUser(email, "not-a-real-hash", "Test User", null);
        return "Bearer " + testJwtService.generateAccessToken(user.getId(), user.getEmail());
    }
}
