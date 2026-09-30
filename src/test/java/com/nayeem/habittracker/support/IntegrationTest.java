package com.nayeem.habittracker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base class for integration tests: the full application against the PostgreSQL container.
 * Extend it rather than repeating the annotations, so every integration test shares one cached
 * context (and one container).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {
}
