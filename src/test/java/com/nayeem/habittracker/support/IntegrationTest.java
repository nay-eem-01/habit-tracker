package com.nayeem.habittracker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Base class for integration tests: the full application against the PostgreSQL container, with
 * MockMvc. Extend it rather than repeating the annotations, so every integration test shares one
 * cached context (and one container).
 */
@SpringBootTest(properties = "app.security.jwt.secret=" + IntegrationTest.TEST_JWT_SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String TEST_JWT_SECRET = "test-only-secret-at-least-32-bytes-long!!";
}
