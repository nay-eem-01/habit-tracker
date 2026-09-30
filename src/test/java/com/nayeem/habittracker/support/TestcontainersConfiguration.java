package com.nayeem.habittracker.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container for the test run. {@code @ServiceConnection} points the datasource at
 * it, so tests need neither a local database nor the {@code db_user_name}/{@code db_password}
 * variables. The container lives as long as Spring's cached test context, so test classes that
 * share a context configuration share the container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
