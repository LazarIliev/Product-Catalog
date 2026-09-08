package com.example.catalog;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real database.
 *
 * <p>The container is a singleton started once per JVM and shared by every test class — the
 * {@code @Testcontainers} extension would instead stop it after the first class, which breaks the
 * second one once Spring has cached its context. Testcontainers' Ryuk sidecar removes it when the
 * build exits.
 *
 * <p>Tests run against the same PostgreSQL version and the same Flyway migrations as production. An
 * in-memory substitute would not exercise the CHECK constraints, the functional unique index or
 * {@code NUMERIC} semantics, which is most of what there is to get wrong here.
 */
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("catalog")
                    .withUsername("catalog")
                    .withPassword("catalog");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
